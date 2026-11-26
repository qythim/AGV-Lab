package com.agvlab.rcs.agv.service;
import org.springframework.stereotype.Service;
import com.agvlab.rcs.agv.repository.RcsAgvRepository;
import com.agvlab.rcs.map.service.MapService;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.dto.AgvResponse;
import com.agvlab.rcs.agv.dto.PlaceAgvRequest;
import com.agvlab.rcs.agv.event.AgvAvailableEvent;
import com.agvlab.rcs.agv.event.SimFleetSyncEvent;
import com.agvlab.rcs.agv.event.SimFleetSyncEvent.SimAgvItem;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AgvService {
    private final RcsAgvRepository rcsAgvRepository;
    private final MapService mapService;
    private final ApplicationEventPublisher eventPublisher;

    public AgvService(RcsAgvRepository rcsAgvRepository,
                      MapService mapService,
                      ApplicationEventPublisher eventPublisher) {
        this.rcsAgvRepository = rcsAgvRepository;
        this.mapService = mapService;
        this.eventPublisher = eventPublisher;
    }

    public RcsAgv createAgv(String agvCode, String agvName, String agvType) {
        if (agvCode == null || agvCode.isBlank()) {
            throw new IllegalArgumentException("AGV 編碼不可為空");
        }
        String normalizedCode = agvCode.strip();
        if (normalizedCode.length() > 64) {
            throw new IllegalArgumentException("AGV 編碼不可超過 64 個字元");
        }
        if (!"STANDARD".equals(agvType) && !"TELESCOPIC".equals(agvType)) {
            throw new IllegalArgumentException("AGV 類型無效");
        }
        String normalizedName = agvName == null ? null : agvName.strip();
        if (normalizedName != null && normalizedName.length() > 128) {
            throw new IllegalArgumentException("AGV 名稱不可超過 128 個字元");
        }
        if (rcsAgvRepository.existsById(normalizedCode)) {
            throw new IllegalStateException("AGV " + normalizedCode + " 已存在");
        }

        RcsAgv agv = new RcsAgv(normalizedCode, agvType);
        agv.setAgvName(normalizedName);
        try {
            return rcsAgvRepository.saveAndFlush(agv);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("AGV " + normalizedCode + " 已存在", exception);
        }
    }


    @Transactional(readOnly = true)
    public List<AgvResponse> getAllAgvs() {
        List<RcsAgv> agvs = rcsAgvRepository.findAll();
        return agvs.stream()
                .map(agv -> new AgvResponse(
                        agv.getAgvCode(),
                        agv.getAgvName(),
                        agv.getAgvType(),
                        agv.getStatus(),
                        agv.getCurrentMapCode(),
                        agv.getCurrentPoint(),
                        agv.getPosX(),
                        agv.getPosY(),
                        agv.getAngle()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgvResponse getAgv(String agvCode) {
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
                .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));
        return new AgvResponse(
                agv.getAgvCode(),
                agv.getAgvName(),
                agv.getAgvType(),
                agv.getStatus(),
                agv.getCurrentMapCode(),
                agv.getCurrentPoint(),
                agv.getPosX(),
                agv.getPosY(),
                agv.getAngle()
        );
    }
    @Transactional
    public RcsAgv placeAgv(String agvCode, PlaceAgvRequest placeAgvRequest) {
        if(placeAgvRequest == null) {
            throw new IllegalArgumentException("放置 AGV 的請求不可為空");
        }
        if (placeAgvRequest.mapCode() == null || placeAgvRequest.mapCode().isBlank()) {
            throw new IllegalArgumentException("地圖代碼不可為空");
        }
        if (placeAgvRequest.pointCode() == null || placeAgvRequest.pointCode().isBlank()) {
            throw new IllegalArgumentException("點位代碼不可為空");
        }
        mapService.lockMapChanges();
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
            .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));

        if(agv.getCurrentMapCode() != null) {
            throw new IllegalStateException("AGV " + agvCode + " 已經在地圖上，無法重新放置");
        }
        var graph = mapService.getGraph(placeAgvRequest.mapCode());
        var point = graph.nodeByCode().get(placeAgvRequest.pointCode());
        if (point == null) throw new NoSuchElementException("找不到指定的點位代碼");
        if (rcsAgvRepository.existsByCurrentMapCodeAndCurrentPoint(placeAgvRequest.mapCode(), placeAgvRequest.pointCode())) {
            throw new IllegalStateException("點位已有 AGV，無法放置");
        }

        agv.setMapContentId(graph.contentId());
        agv.setPosX(point.x());
        agv.setPosY(point.y());
        agv.setAngle(0.0);
        agv.setCurrentMapCode(placeAgvRequest.mapCode());
        agv.setCurrentPoint(placeAgvRequest.pointCode());
        RcsAgv saved = rcsAgvRepository.saveAndFlush(agv);
        if ("IDLE".equals(saved.getStatus())) {
            eventPublisher.publishEvent(new AgvAvailableEvent(saved.getAgvCode(), saved.getCurrentMapCode()));
        }
        publishFleetSyncAfterCommit(
                "PLACE",
                saved.getAgvCode(),
                saved.getCurrentMapCode(),
                saved.getCurrentPoint(),
                point.x(),
                point.y(),
                Math.toRadians(saved.getAngle() != null ? saved.getAngle() : 0.0)
        );
        return saved;
    }

    @Transactional
    public RcsAgv unplaceAgv(String agvCode) {
        mapService.lockMapChanges();
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
            .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));

        if (agv.getCurrentMapCode() == null) {
            throw new IllegalStateException("AGV " + agvCode + " 尚未放置在地圖上");
        }
        if (agv.getCurrentTaskId() != null || rcsAgvRepository.hasOpenTask(agvCode)) {
            throw new IllegalStateException("AGV " + agvCode + " 尚有未完成的任務，無法移出地圖");
        }
        if (!"IDLE".equals(agv.getStatus())) {
            throw new IllegalStateException("AGV " + agvCode + " 目前不是待命狀態，無法移出地圖");
        }
        if (rcsAgvRepository.hasPointLock(agvCode)) {
            throw new IllegalStateException("AGV " + agvCode + " 尚有點位鎖定，無法移出地圖");
        }

        agv.setMapContentId(null);
        agv.setCurrentMapCode(null);
        agv.setCurrentPoint(null);
        agv.setPosX(null);
        agv.setPosY(null);
        agv.setAngle(0.0);
        RcsAgv saved = rcsAgvRepository.saveAndFlush(agv);
        publishFleetSyncAfterCommit("DESTROY", saved.getAgvCode(), "", "", 0.0, 0.0, 0.0);
        return saved;
    }

    /**
     * 系統啟動完成後發布一次車隊全量快照至 MQTT (retain=True)，供 ROS 2 模擬器同步既有車輛
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void syncFleetSnapshotOnStartup() {
        List<SimAgvItem> snapshot = buildPlacedFleetSnapshot();
        eventPublisher.publishEvent(new SimFleetSyncEvent("SNAPSHOT", "", "", "", 0.0, 0.0, 0.0, snapshot));
    }

    /**
     * 供外部服務 (如 TaskService) 於車輛抵達新節點或取消任務後呼叫，
     * 於交易提交後即時刷新 MQTT 保留主題 sim/fleet/snapshot，避免 ROS 2 重啟時載入過期座標。
     */
    public void publishFleetSnapshotAfterCommit() {
        publishFleetSyncAfterCommit("SNAPSHOT", "", "", "", 0.0, 0.0, 0.0);
    }

    private void publishFleetSyncAfterCommit(String action, String agvId, String mapId,
                                             String pointId, double x, double y, double thetaRad) {
        List<SimAgvItem> snapshot = buildPlacedFleetSnapshot();
        SimFleetSyncEvent syncEvent = new SimFleetSyncEvent(action, agvId, mapId, pointId, x, y, thetaRad, snapshot);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(syncEvent);
                }
            });
        } else {
            eventPublisher.publishEvent(syncEvent);
        }
    }

    private List<SimAgvItem> buildPlacedFleetSnapshot() {
        return rcsAgvRepository.findAll().stream()
                .filter(a -> a.getCurrentMapCode() != null
                        && a.getCurrentPoint() != null
                        && a.getPosX() != null
                        && a.getPosY() != null)
                .map(a -> new SimAgvItem(
                        a.getAgvCode(),
                        a.getCurrentMapCode(),
                        a.getCurrentPoint(),
                        a.getPosX(),
                        a.getPosY(),
                        Math.toRadians(a.getAngle() != null ? a.getAngle() : 0.0)
                ))
                .toList();
    }
}
