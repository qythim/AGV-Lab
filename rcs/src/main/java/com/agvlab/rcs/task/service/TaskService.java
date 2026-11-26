package com.agvlab.rcs.task.service;

import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.event.AgvAvailableEvent;
import com.agvlab.rcs.agv.repository.RcsAgvRepository;
import com.agvlab.rcs.agv.service.AgvService;
import com.agvlab.rcs.map.dto.MapDataDto.PointDto;
import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.map.service.MapService;
import com.agvlab.rcs.pathfinding.dto.FindPathResponse;
import com.agvlab.rcs.pathfinding.service.FindPathService;
import com.agvlab.rcs.task.dto.CreateTaskRequest;
import com.agvlab.rcs.task.dto.TaskDetailResponse;
import com.agvlab.rcs.task.dto.TaskResponse;
import com.agvlab.rcs.task.entity.RcsTask;
import com.agvlab.rcs.task.entity.RcsTaskDetail;
import com.agvlab.rcs.task.repository.RcsTaskDetailRepository;
import com.agvlab.rcs.task.repository.RcsTaskRepository;
import com.agvlab.rcs.traffic.manager.NavigationCompletedEvent;
import com.agvlab.rcs.traffic.manager.TrafficManagerService;
import com.agvlab.rcs.traffic.reservation.service.ReservationService;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 搬運任務管理服務 (Task Manager)
 * 職責：
 * 1. 建立任務並驗證地圖與起迄點位合法性，於交易提交後非同步觸發派發 (不阻塞 HTTP 請求)
 * 2. 透過單執行緒派發器逐筆獨立交易配對車輛 (Auto Assign 依距離由近至遠尋找具可行路徑之 IDLE 車輛)
 * 3. 支援兩階段搬運流程：第一階段前往取貨點 (:PICKUP)，第二階段前往卸貨點 (:DELIVERY)
 * 4. 純事件驅動喚醒：監聽 NavigationCompletedEvent、AgvAvailableEvent 與 ApplicationReadyEvent
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String LEG_PICKUP_SUFFIX = ":PICKUP";
    private static final String LEG_DELIVERY_SUFFIX = ":DELIVERY";

    // 單筆任務提交資料庫後，實際下發給 TrafficManager 的指令封裝
    private record DispatchCommand(
            String mapCode,
            String agvCode,
            String orderId,
            String startPoint,
            List<String> fullPath
    ) {}

    // 單執行緒背景派發器：天然序列執行，避免多執行緒重複指派同一台 AGV，且不阻塞 HTTP 請求
    private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rcs-task-dispatcher");
        t.setDaemon(true);
        return t;
    });

    private final RcsTaskRepository rcsTaskRepository;
    private final RcsTaskDetailRepository rcsTaskDetailRepository;
    private final RcsAgvRepository rcsAgvRepository;
    private final AgvService agvService;
    private final MapService mapService;
    private final FindPathService findPathService;
    private final TrafficManagerService trafficManagerService;
    private final ReservationService reservationService;
    private final TransactionTemplate transactionTemplate;

    public TaskService(RcsTaskRepository rcsTaskRepository,
                       RcsTaskDetailRepository rcsTaskDetailRepository,
                       RcsAgvRepository rcsAgvRepository,
                       AgvService agvService,
                       MapService mapService,
                       FindPathService findPathService,
                       TrafficManagerService trafficManagerService,
                       ReservationService reservationService,
                       TransactionTemplate transactionTemplate) {
        this.rcsTaskRepository = rcsTaskRepository;
        this.rcsTaskDetailRepository = rcsTaskDetailRepository;
        this.rcsAgvRepository = rcsAgvRepository;
        this.agvService = agvService;
        this.mapService = mapService;
        this.findPathService = findPathService;
        this.trafficManagerService = trafficManagerService;
        this.reservationService = reservationService;
        this.transactionTemplate = transactionTemplate;
    }

    @PreDestroy
    public void shutdown() {
        dispatchExecutor.shutdownNow();
    }

    /**
     * 建立新搬運任務：僅儲存為 PENDING 並於交易提交後非同步觸發派發，立即回應 API
     */
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("任務請求不可為空");
        }
        if (request.mapCode() == null || request.mapCode().isBlank()) {
            throw new IllegalArgumentException("地圖代碼不可為空");
        }
        if (request.startPoint() == null || request.startPoint().isBlank()
                || request.targetPoint() == null || request.targetPoint().isBlank()) {
            throw new IllegalArgumentException("起點與終點代碼不可為空");
        }
        String mapCode = request.mapCode().strip();
        String startPoint = request.startPoint().strip();
        String targetPoint = request.targetPoint().strip();
        if (startPoint.equals(targetPoint)) {
            throw new IllegalArgumentException("任務起點與終點不可相同");
        }

        MapGraph graph = mapService.getGraph(mapCode);
        if (!graph.nodeByCode().containsKey(startPoint)) {
            throw new NoSuchElementException("地圖中找不到起點點位: " + startPoint);
        }
        if (!graph.nodeByCode().containsKey(targetPoint)) {
            throw new NoSuchElementException("地圖中找不到終點點位: " + targetPoint);
        }

        String normalizedAgvCode = (request.agvCode() == null || request.agvCode().isBlank())
                ? null
                : request.agvCode().strip();
        if (normalizedAgvCode != null && !rcsAgvRepository.existsById(normalizedAgvCode)) {
            throw new NoSuchElementException("找不到指定的 AGV 代碼: " + normalizedAgvCode);
        }

        int targetLevel = (request.targetLevel() == null || request.targetLevel() < 0)
                ? 0
                : request.targetLevel();

        String taskCode = "TASK-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        RcsTask task = new RcsTask();
        task.setTaskCode(taskCode);
        task.setMapCode(mapCode);
        task.setMapContentId(graph.contentId());
        task.setAgvCode(normalizedAgvCode);
        task.setStartPoint(startPoint);
        task.setTargetPoint(targetPoint);
        task.setTargetLevel(targetLevel);
        task.setStatus("PENDING");

        rcsTaskRepository.saveAndFlush(task);
        log.info("已建立搬運任務 [{}]: 地圖={}, 指定車輛={}, 起點={}, 終點={}",
                taskCode, mapCode, normalizedAgvCode != null ? normalizedAgvCode : "AUTO", startPoint, targetPoint);

        // 待交易提交完成後，於背景單執行緒非同步觸發派發，不阻塞 HTTP 回應
        triggerDispatchAfterCommit();
        return TaskResponse.from(task);
    }

    /**
     * 查詢所有任務清單 (依建立時間由新到舊排序)
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getAllTasks(String status) {
        List<RcsTask> tasks = (status == null || status.isBlank())
                ? rcsTaskRepository.findAllByOrderByCreatedAtDesc()
                : rcsTaskRepository.findByStatusOrderByCreatedAtAsc(status.strip().toUpperCase());
        return tasks.stream().map(TaskResponse::from).toList();
    }

    /**
     * 查詢單筆任務詳情
     */
    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskCode) {
        RcsTask task = rcsTaskRepository.findById(taskCode)
                .orElseThrow(() -> new NoSuchElementException("找不到指定的任務代碼: " + taskCode));
        return TaskResponse.from(task);
    }

    /**
     * 查詢指定任務的各階段執行明細 (依 step_seq 排序)
     */
    @Transactional(readOnly = true)
    public List<TaskDetailResponse> getTaskDetails(String taskCode) {
        if (!rcsTaskRepository.existsById(taskCode)) {
            throw new NoSuchElementException("找不到指定的任務代碼: " + taskCode);
        }
        return rcsTaskDetailRepository.findByTaskCodeOrderByStepSeqAsc(taskCode)
                .stream()
                .map(TaskDetailResponse::from)
                .toList();
    }

    /**
     * 取消任務 (僅限 PENDING 或 RUNNING 狀態)
     */
    @Transactional
    public TaskResponse cancelTask(String taskCode) {
        RcsTask task = rcsTaskRepository.findById(taskCode)
                .orElseThrow(() -> new NoSuchElementException("找不到指定的任務代碼: " + taskCode));

        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            throw new IllegalStateException("任務 [" + taskCode + "] 已結束，無法取消");
        }

        if ("RUNNING".equals(task.getStatus()) && task.getAgvCode() != null) {
            String agvCode = task.getAgvCode();
            String lastKnownPoint = trafficManagerService.getLastPoint(agvCode);
            trafficManagerService.cancelNavigation(agvCode);
            rcsAgvRepository.findById(agvCode).ifPresent(agv -> {
                // 取消任務前先同步車輛最後回報點位與座標至資料庫，防止模擬器重啟後位置錯位
                if (lastKnownPoint != null && !lastKnownPoint.isBlank()) {
                    agv.setCurrentPoint(lastKnownPoint);
                    if (agv.getCurrentMapCode() != null) {
                        try {
                            MapGraph graph = mapService.getGraph(agv.getCurrentMapCode());
                            PointDto pt = graph.nodeByCode().get(lastKnownPoint);
                            if (pt != null) {
                                agv.setPosX(pt.x());
                                agv.setPosY(pt.y());
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (agv.getCurrentPoint() != null) {
                    reservationService.releaseExceptOccupied(agvCode, agv.getCurrentPoint());
                }
                agv.setStatus("IDLE");
                agv.setCurrentTaskId(null);
                rcsAgvRepository.saveAndFlush(agv);
                agvService.publishFleetSnapshotAfterCommit();
            });
        }

        OffsetDateTime now = OffsetDateTime.now();
        task.setStatus("FAILED");
        task.setFinishedAt(now);
        rcsTaskRepository.saveAndFlush(task);

        // 將尚未完成的階段明細一併標記為 FAILED
        List<RcsTaskDetail> details = rcsTaskDetailRepository.findByTaskCodeOrderByStepSeqAsc(taskCode);
        for (RcsTaskDetail detail : details) {
            if ("PENDING".equals(detail.getStatus()) || "RUNNING".equals(detail.getStatus())) {
                detail.setStatus("FAILED");
                detail.setFinishedAt(now);
                rcsTaskDetailRepository.save(detail);
            }
        }
        rcsTaskDetailRepository.flush();
        log.info("已取消任務 [{}]，狀態更新為 FAILED", taskCode);

        // 若剛釋放了車輛，於交易提交後非同步派發後續等待中的任務
        triggerDispatchAfterCommit();
        return TaskResponse.from(task);
    }

    /**
     * 系統啟動完成後執行一次，接手資料庫中遺留的 PENDING 任務
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        triggerDispatchAfterCommit();
    }

    /**
     * 監聽車輛可用事件 (例如新放置到地圖上或恢復 IDLE)，即時喚醒等待中的 PENDING 任務
     */
    @EventListener
    public void onAgvAvailable(AgvAvailableEvent event) {
        log.info("收到車輛 [{}] 可用事件 (地圖: {})，觸發檢查待派發任務", event.agvCode(), event.mapCode());
        triggerDispatchAfterCommit();
    }

    /**
     * 監聽導航路段完成事件：
     * - 若完成的是第一階段 (:PICKUP)，自動計算並下發第二階段 (:DELIVERY)
     * - 若完成的是第二階段 (:DELIVERY)，將任務標記為 COMPLETED、釋放車輛為 IDLE 並派發下一筆任務
     */
    @EventListener
    public void onNavigationCompleted(NavigationCompletedEvent event) {
        DispatchCommand nextLegCommand = transactionTemplate.execute(status -> handleNavigationCompletedInTx(event));
        if (nextLegCommand != null) {
            trafficManagerService.startNavigation(
                    nextLegCommand.mapCode(),
                    nextLegCommand.agvCode(),
                    nextLegCommand.orderId(),
                    nextLegCommand.startPoint(),
                    nextLegCommand.fullPath()
            );
        }
    }

    private DispatchCommand handleNavigationCompletedInTx(NavigationCompletedEvent event) {
        String agvCode = event.agvCode();
        String arrivedPoint = event.arrivedPoint();
        String orderId = event.orderId();

        RcsAgv agv = rcsAgvRepository.findById(agvCode).orElse(null);
        if (agv != null && arrivedPoint != null) {
            agv.setCurrentPoint(arrivedPoint);
            if (agv.getCurrentMapCode() != null) {
                try {
                    MapGraph graph = mapService.getGraph(agv.getCurrentMapCode());
                    PointDto pt = graph.nodeByCode().get(arrivedPoint);
                    if (pt != null) {
                        agv.setPosX(pt.x());
                        agv.setPosY(pt.y());
                    }
                } catch (Exception ignored) {}
            }
        }

        RcsTask task = rcsTaskRepository.findFirstByAgvCodeAndStatus(agvCode, "RUNNING").orElse(null);
        if (task == null) {
            if (agv != null) {
                agv.setStatus("IDLE");
                agv.setCurrentTaskId(null);
                rcsAgvRepository.saveAndFlush(agv);
                agvService.publishFleetSnapshotAfterCommit();
                triggerDispatchAfterCommit();
            }
            return null;
        }

        // 若完成的是第一階段 (抵達取貨點 startPoint)，緊接著規劃第二階段 (startPoint -> targetPoint)
        if (orderId != null && orderId.endsWith(LEG_PICKUP_SUFFIX)) {
            OffsetDateTime now = OffsetDateTime.now();
            if (agv != null) {
                rcsAgvRepository.saveAndFlush(agv);
                // 抵達取貨點後同步刷新 MQTT 保留快照，確保模擬器重啟時位置一致
                agvService.publishFleetSnapshotAfterCommit();
            }

            // 更新第一階段明細為 COMPLETED
            rcsTaskDetailRepository.findByTaskCodeAndStepType(task.getTaskCode(), "PICKUP")
                    .ifPresent(pickupDetail -> {
                        pickupDetail.setStatus("COMPLETED");
                        pickupDetail.setFinishedAt(now);
                        rcsTaskDetailRepository.saveAndFlush(pickupDetail);
                    });

            log.info("車輛 [{}] 已抵達任務 [{}] 取貨點 [{}]，開始啟動第二階段前往卸貨點 [{}]",
                    agvCode, task.getTaskCode(), task.getStartPoint(), task.getTargetPoint());

            FindPathResponse deliveryRes = findPathService.findPath_Astar(
                    task.getMapCode(), task.getStartPoint(), task.getTargetPoint());
            List<String> deliveryPath = deliveryRes.pointCodes();

            if (deliveryPath == null || deliveryPath.isEmpty()) {
                log.error("任務 [{}] 第二階段找不到從 [{}] 至 [{}] 的路徑，標記為 FAILED",
                        task.getTaskCode(), task.getStartPoint(), task.getTargetPoint());
                task.setStatus("FAILED");
                task.setFinishedAt(now);
                rcsTaskRepository.saveAndFlush(task);

                rcsTaskDetailRepository.findByTaskCodeAndStepType(task.getTaskCode(), "DELIVERY")
                        .ifPresent(deliveryDetail -> {
                            deliveryDetail.setStatus("FAILED");
                            deliveryDetail.setFinishedAt(now);
                            rcsTaskDetailRepository.saveAndFlush(deliveryDetail);
                        });

                if (agv != null) {
                    agv.setStatus("IDLE");
                    agv.setCurrentTaskId(null);
                    rcsAgvRepository.saveAndFlush(agv);
                }
                triggerDispatchAfterCommit();
                return null;
            }

            // 更新第二階段明細為 RUNNING 並記錄開始時間
            rcsTaskDetailRepository.findByTaskCodeAndStepType(task.getTaskCode(), "DELIVERY")
                    .ifPresent(deliveryDetail -> {
                        deliveryDetail.setStatus("RUNNING");
                        deliveryDetail.setStartedAt(now);
                        rcsTaskDetailRepository.saveAndFlush(deliveryDetail);
                    });

            return new DispatchCommand(
                    task.getMapCode(),
                    agvCode,
                    task.getTaskCode() + LEG_DELIVERY_SUFFIX,
                    task.getStartPoint(),
                    deliveryPath
            );
        }

        // 第二階段完成：標記任務與 DELIVERY 明細為 COMPLETED 並釋放車輛
        OffsetDateTime finishedAt = OffsetDateTime.now();
        task.setStatus("COMPLETED");
        task.setFinishedAt(finishedAt);
        rcsTaskRepository.saveAndFlush(task);

        rcsTaskDetailRepository.findByTaskCodeAndStepType(task.getTaskCode(), "DELIVERY")
                .ifPresent(deliveryDetail -> {
                    deliveryDetail.setStatus("COMPLETED");
                    deliveryDetail.setFinishedAt(finishedAt);
                    rcsTaskDetailRepository.saveAndFlush(deliveryDetail);
                });

        if (agv != null) {
            agv.setStatus("IDLE");
            agv.setCurrentTaskId(null);
            rcsAgvRepository.saveAndFlush(agv);
            // 任務完成後立即刷新 MQTT 保留快照 sim/fleet/snapshot，確保與資料庫位置 100% 同步
            agvService.publishFleetSnapshotAfterCommit();
        }

        log.info("任務 [{}] 已由車輛 [{}] 順利完成，最終位置: [{}]", task.getTaskCode(), agvCode, arrivedPoint);

        // 車輛恢復 IDLE 後，於交易提交後非同步派發等待中的 PENDING 任務
        triggerDispatchAfterCommit();
        return null;
    }

    /**
     * 在當前交易提交後 (或無交易時立即) 向單執行緒派發器送出派發請求
     */
    private void triggerDispatchAfterCommit() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchExecutor.submit(TaskService.this::tryDispatchPendingTasks);
                }
            });
        } else {
            dispatchExecutor.submit(this::tryDispatchPendingTasks);
        }
    }

    /**
     * 背景掃描所有 PENDING 任務，逐筆以獨立交易配對可用車輛；
     * 每筆任務於資料庫提交成功後才呼叫 TrafficManagerService 下發訂單，確保不會發生跨任務回滾。
     */
    public void tryDispatchPendingTasks() {
        try {
            List<RcsTask> pendingTasks = rcsTaskRepository.findByStatusOrderByCreatedAtAsc("PENDING");
            if (pendingTasks.isEmpty()) {
                return;
            }

            // 記錄本輪已確認「完全無任何 IDLE 車輛」的地圖，避免同地圖後續自動選車任務重複查詢資料庫
            Set<String> emptyAutoMaps = new HashSet<>();

            for (RcsTask snapshot : pendingTasks) {
                boolean isAutoAssign = (snapshot.getAgvCode() == null || snapshot.getAgvCode().isBlank());
                if (isAutoAssign && emptyAutoMaps.contains(snapshot.getMapCode())) {
                    continue;
                }

                DispatchCommand command = transactionTemplate.execute(
                        status -> dispatchSingleTaskInTx(snapshot.getTaskCode(), emptyAutoMaps)
                );

                // 僅在該任務的獨立資料庫交易提交成功後，才通知 TrafficManager 下發 VDA 5050 訂單
                if (command != null) {
                    trafficManagerService.startNavigation(
                            command.mapCode(),
                            command.agvCode(),
                            command.orderId(),
                            command.startPoint(),
                            command.fullPath()
                    );
                }
            }
        } catch (Exception e) {
            log.error("執行待派發任務調度時發生異常: {}", e.getMessage(), e);
        }
    }

    /**
     * 於獨立交易中嘗試派發單筆 PENDING 任務
     */
    private DispatchCommand dispatchSingleTaskInTx(String taskCode, Set<String> emptyAutoMaps) {
        RcsTask task = rcsTaskRepository.findById(taskCode).orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            return null;
        }

        MapGraph graph;
        try {
            graph = mapService.getGraph(task.getMapCode());
        } catch (Exception e) {
            log.warn("任務 [{}] 載入地圖 [{}] 失敗: {}", task.getTaskCode(), task.getMapCode(), e.getMessage());
            return null;
        }

        List<RcsAgv> candidates = findCandidateAgvs(task, graph);
        if (candidates.isEmpty()) {
            if (task.getAgvCode() == null || task.getAgvCode().isBlank()) {
                emptyAutoMaps.add(task.getMapCode());
            }
            return null;
        }

        // 依距離由近至遠嘗試候選車輛，挑選第一台具有可行 A* 路徑的車輛
        for (RcsAgv agv : candidates) {
            String currentPoint = agv.getCurrentPoint();
            boolean needPickupLeg = !currentPoint.equals(task.getStartPoint());
            String legStart = currentPoint;
            String legTarget = needPickupLeg ? task.getStartPoint() : task.getTargetPoint();
            String orderId = task.getTaskCode() + (needPickupLeg ? LEG_PICKUP_SUFFIX : LEG_DELIVERY_SUFFIX);

            FindPathResponse pathRes = findPathService.findPath_Astar(task.getMapCode(), legStart, legTarget);
            List<String> fullPath = pathRes.pointCodes();
            if (fullPath == null || fullPath.isEmpty()) {
                log.warn("候選車輛 [{}] 無法從 [{}] 規劃路徑至 [{}]，嘗試下一台候選車輛",
                        agv.getAgvCode(), legStart, legTarget);
                continue;
            }

            // 更新任務與車輛狀態為 RUNNING
            OffsetDateTime now = OffsetDateTime.now();
            task.setAgvCode(agv.getAgvCode());
            task.setStatus("RUNNING");
            rcsTaskRepository.saveAndFlush(task);

            agv.setStatus("RUNNING");
            agv.setCurrentTaskId(task.getTaskCode());
            rcsAgvRepository.saveAndFlush(agv);

            // 建立任務階段明細 (rcs_task_detail)
            createTaskDetails(task, agv.getAgvCode(), currentPoint, needPickupLeg, now);

            log.info("任務 [{}] 已指派給車輛 [{}]，啟動{}路段: {} -> {}",
                    task.getTaskCode(), agv.getAgvCode(),
                    needPickupLeg ? "第一階段(前往取貨點)" : "第二階段(前往卸貨點)",
                    legStart, legTarget);

            return new DispatchCommand(task.getMapCode(), agv.getAgvCode(), orderId, legStart, fullPath);
        }

        // 所有候選 IDLE 車輛皆無可行路徑可抵達目標點，標記任務為 FAILED
        log.error("任務 [{}] 嘗試所有可用車輛後皆找不到可行路徑，標記為 FAILED", task.getTaskCode());
        task.setStatus("FAILED");
        task.setFinishedAt(OffsetDateTime.now());
        rcsTaskRepository.saveAndFlush(task);
        return null;
    }

    /**
     * 取得候選可用車輛清單：
     * - 指定車輛：若該車於同地圖處於 IDLE，回傳單筆清單
     * - 自動選車：回傳同地圖所有 IDLE 車輛並依距離 startPoint 由近至遠排序
     */
    private List<RcsAgv> findCandidateAgvs(RcsTask task, MapGraph graph) {
        if (task.getAgvCode() != null && !task.getAgvCode().isBlank()) {
            return rcsAgvRepository.findById(task.getAgvCode())
                    .filter(agv -> isAgvAvailableOnMap(agv, task.getMapCode()))
                    .map(List::of)
                    .orElse(List.of());
        }

        PointDto pickupNode = graph.nodeByCode().get(task.getStartPoint());
        return rcsAgvRepository.findByCurrentMapCode(task.getMapCode())
                .stream()
                .filter(agv -> isAgvAvailableOnMap(agv, task.getMapCode()))
                .sorted(Comparator.comparingDouble(agv -> distanceToPickup(agv, pickupNode, graph)))
                .toList();
    }

    private boolean isAgvAvailableOnMap(RcsAgv agv, String mapCode) {
        return mapCode.equals(agv.getCurrentMapCode())
                && "IDLE".equals(agv.getStatus())
                && agv.getCurrentTaskId() == null
                && agv.getCurrentPoint() != null
                && !agv.getCurrentPoint().isBlank();
    }

    private double distanceToPickup(RcsAgv agv, PointDto pickupNode, MapGraph graph) {
        if (pickupNode == null) {
            return 0.0;
        }
        PointDto agvNode = graph.nodeByCode().get(agv.getCurrentPoint());
        double ax = (agvNode != null) ? agvNode.x() : (agv.getPosX() != null ? agv.getPosX() : 0.0);
        double ay = (agvNode != null) ? agvNode.y() : (agv.getPosY() != null ? agv.getPosY() : 0.0);
        double dx = pickupNode.x() - ax;
        double dy = pickupNode.y() - ay;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 依任務是否需要第一階段取貨，初始化 rcs_task_detail 階段明細紀錄
     */
    private void createTaskDetails(RcsTask task, String agvCode, String currentPoint,
                                   boolean needPickupLeg, OffsetDateTime now) {
        if (needPickupLeg) {
            RcsTaskDetail pickupStep = new RcsTaskDetail();
            pickupStep.setTaskCode(task.getTaskCode());
            pickupStep.setStepSeq(1);
            pickupStep.setStepType("PICKUP");
            pickupStep.setAgvCode(agvCode);
            pickupStep.setFromPoint(currentPoint);
            pickupStep.setToPoint(task.getStartPoint());
            pickupStep.setStatus("RUNNING");
            pickupStep.setStartedAt(now);

            RcsTaskDetail deliveryStep = new RcsTaskDetail();
            deliveryStep.setTaskCode(task.getTaskCode());
            deliveryStep.setStepSeq(2);
            deliveryStep.setStepType("DELIVERY");
            deliveryStep.setAgvCode(agvCode);
            deliveryStep.setFromPoint(task.getStartPoint());
            deliveryStep.setToPoint(task.getTargetPoint());
            deliveryStep.setStatus("PENDING");

            rcsTaskDetailRepository.saveAllAndFlush(List.of(pickupStep, deliveryStep));
        } else {
            RcsTaskDetail deliveryStep = new RcsTaskDetail();
            deliveryStep.setTaskCode(task.getTaskCode());
            deliveryStep.setStepSeq(1);
            deliveryStep.setStepType("DELIVERY");
            deliveryStep.setAgvCode(agvCode);
            deliveryStep.setFromPoint(task.getStartPoint());
            deliveryStep.setToPoint(task.getTargetPoint());
            deliveryStep.setStatus("RUNNING");
            deliveryStep.setStartedAt(now);

            rcsTaskDetailRepository.saveAndFlush(deliveryStep);
        }
    }
}
