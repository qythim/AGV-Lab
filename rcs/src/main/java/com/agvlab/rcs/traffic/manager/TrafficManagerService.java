package com.agvlab.rcs.traffic.manager;

import com.agvlab.rcs.agv.vda5050.AgvStateReportEvent;
import com.agvlab.rcs.agv.vda5050.Vda5050MqttService;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateRequest;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;
import com.agvlab.rcs.traffic.reservation.event.PointReleasedEvent;
import com.agvlab.rcs.traffic.reservation.service.RedisService;
import com.agvlab.rcs.traffic.reservation.service.ReservationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交通調度核心服務 (Traffic Manager)
 * 職責：
 * 1. 接收車輛狀態回報事件 (AgvStateReportEvent)，依節點變化或節流時間觸發滑動視窗申請
 * 2. 申請遭阻擋時，將車輛依 FIFO 順序寫入 Redis ZSET 排隊佇列 (TrafficWait:<pointCode>)
 * 3. 監聽點位釋放事件 (PointReleasedEvent)，即時喚醒排隊中的首台車輛繼續前進
 * 4. 取得通行權後，呼叫 Vda5050MqttService 下發 VDA 5050 Order 訂單
 */
@Service
public class TrafficManagerService {

    private static final Logger log = LoggerFactory.getLogger(TrafficManagerService.class);

    // 預設滑動視窗大小與交通檢查節流間隔 (毫秒)
    private static final int DEFAULT_WINDOW_SIZE = 2;
    private static final long CHECK_INTERVAL_MS = 500;

    // 車輛執行中路徑與狀態快取 (未來可由 TaskService 接管路徑生命週期)
    private final Map<String, List<String>> activePathMap = new ConcurrentHashMap<>();
    private final Map<String, String> agvMapCodeMap = new ConcurrentHashMap<>();
    private final Map<String, String> lastPointMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCheckTimeMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> lastSentPointsMap = new ConcurrentHashMap<>();

    private final ReservationService reservationService;
    private final RedisService redisService;
    private final Vda5050MqttService vda5050MqttService;

    public TrafficManagerService(ReservationService reservationService,
                                 RedisService redisService,
                                 Vda5050MqttService vda5050MqttService) {
        this.reservationService = reservationService;
        this.redisService = redisService;
        this.vda5050MqttService = vda5050MqttService;
    }

    /**
     * 註冊車輛的導航路徑並立即申請起點安全視窗
     */
    public ProgressUpdateResponse startNavigation(String mapCode, String agvCode, String startPoint, List<String> fullPath) {
        activePathMap.put(agvCode, fullPath);
        agvMapCodeMap.put(agvCode, mapCode);
        lastPointMap.put(agvCode, startPoint);
        lastCheckTimeMap.put(agvCode, System.currentTimeMillis());
        lastSentPointsMap.remove(agvCode);

        log.info("已註冊車輛 [{}] 導航路徑 (地圖: {}): {}", agvCode, mapCode, fullPath);
        return requestMove(mapCode, agvCode, startPoint);
    }

    /**
     * 監聽 MQTT 層發布的車輛位置狀態回報事件
     */
    @EventListener
    public void onAgvStateReport(AgvStateReportEvent event) {
        String agvCode = event.agvCode();
        String currentPoint = event.currentPoint();
        if (agvCode == null || agvCode.isEmpty() || currentPoint == null || currentPoint.isEmpty()) {
            return;
        }

        if (event.mapCode() != null && !event.mapCode().isEmpty()) {
            agvMapCodeMap.put(agvCode, event.mapCode());
        }

        if (!activePathMap.containsKey(agvCode)) {
            return;
        }

        if (!shouldTriggerTrafficCheck(agvCode, currentPoint)) {
            return;
        }

        String mapCode = agvMapCodeMap.getOrDefault(agvCode, event.mapCode());
        requestMove(mapCode, agvCode, currentPoint);
    }

    /**
     * 監聽點位釋放事件：依 FIFO 順序喚醒正在等待該點位的車輛
     */
    @EventListener
    public void onPointReleased(PointReleasedEvent event) {
        if (event.releasedPoints() == null || event.releasedPoints().isEmpty()) {
            return;
        }

        for (String pointCode : event.releasedPoints()) {
            Set<String> waitingAgvs = redisService.getWaitingAgvs(pointCode);
            if (waitingAgvs == null || waitingAgvs.isEmpty()) {
                continue;
            }

            log.info("點位 [{}] 已釋放，開始依 FIFO 喚醒排隊車輛: {}", pointCode, waitingAgvs);
            for (String waitingAgv : waitingAgvs) {
                String currentPoint = lastPointMap.get(waitingAgv);
                String mapCode = agvMapCodeMap.get(waitingAgv);

                // 若車輛已無執行中路徑或查無目前點位，直接從排隊佇列移除
                if (!activePathMap.containsKey(waitingAgv) || currentPoint == null) {
                    redisService.removeWait(waitingAgv, pointCode);
                    continue;
                }

                ProgressUpdateResponse res = requestMove(mapCode, waitingAgv, currentPoint);
                if (res != null && res.canMove()) {
                    redisService.removeWait(waitingAgv, pointCode);
                    log.info("成功喚醒車輛 [{}]，已取得點位 [{}] 通行權並下發訂單", waitingAgv, pointCode);
                    // 該點位已分配給隊首車輛，結束本輪喚醒
                    break;
                } else if (res != null && !pointCode.equals(res.blockPoint())) {
                    // 若阻擋點已變更 (不再受此點位阻擋)，清除舊的排隊紀錄
                    redisService.removeWait(waitingAgv, pointCode);
                }
            }
        }
    }

    /**
     * 向 ReservationService 申請滑動視窗通行權：
     * - 通過：若持有點位清單有更新，則下發 VDA 5050 Order
     * - 遭阻擋：將車輛加入阻擋點位的 FIFO 排隊佇列
     */
    private ProgressUpdateResponse requestMove(String mapCode, String agvCode, String currentPoint) {
        List<String> fullPath = activePathMap.get(agvCode);
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }

        ProgressUpdateResponse res = reservationService.updateProgress(
                new ProgressUpdateRequest(mapCode, agvCode, currentPoint, fullPath, DEFAULT_WINDOW_SIZE)
        );

        if (res.canMove() && !res.holdingPoints().isEmpty()) {
            List<String> lastSent = lastSentPointsMap.get(agvCode);
            if (!res.holdingPoints().equals(lastSent)) {
                lastSentPointsMap.put(agvCode, List.copyOf(res.holdingPoints()));
                vda5050MqttService.sendOrder(agvCode, "order_" + agvCode, res.holdingPoints());
            }
        } else if (res.blockPoint() != null) {
            Boolean added = redisService.enqueueWait(agvCode, res.blockPoint());
            if (Boolean.TRUE.equals(added)) {
                log.info("車輛 [{}] 於點位 [{}] 受阻，已加入排隊佇列 TrafficWait:{}",
                        agvCode, res.blockPoint(), res.blockPoint());
            }
        }

        return res;
    }

    /**
     * 節流判斷：僅在車輛切換節點或距離上次檢查超過 500ms 時才觸發交通申請
     */
    private boolean shouldTriggerTrafficCheck(String agvCode, String currentPoint) {
        long now = System.currentTimeMillis();
        String lastPoint = lastPointMap.get(agvCode);
        long lastTime = lastCheckTimeMap.getOrDefault(agvCode, 0L);

        boolean pointChanged = !currentPoint.equals(lastPoint);
        boolean timeout = (now - lastTime) >= CHECK_INTERVAL_MS;

        if (pointChanged || timeout) {
            lastPointMap.put(agvCode, currentPoint);
            lastCheckTimeMap.put(agvCode, now);
            return true;
        }
        return false;
    }
}

