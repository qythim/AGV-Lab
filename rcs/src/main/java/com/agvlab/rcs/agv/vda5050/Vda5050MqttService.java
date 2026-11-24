package com.agvlab.rcs.agv.vda5050;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.agvlab.rcs.agv.event.SimFleetSyncEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.agvlab.rcs.map.dto.MapDataDto.LineDto;
import com.agvlab.rcs.map.dto.MapDataDto.PointDto;
import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.websocket.handler.AgvWebSocketHandler;

/**
 * VDA 5050 MQTT 通訊服務 (Spring Boot 與 Mosquitto 橋接核心)
 * 職責：
 * 1. 於系統啟動時建立與 MQTT Broker 的長連線並訂閱車輛狀態主題
 * 2. 將高頻車輛座標差量推送至前端 WebSocket (AGV_POSE)
 * 3. 發布 AgvStateReportEvent 事件交由 TrafficManager 處理交通調度
 * 4. 提供向車輛發送 VDA 5050 Order 訂單的方法
 */
@Service
public class Vda5050MqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(Vda5050MqttService.class);

    // 用於比對座標或狀態是否發生變化 (Delta Update)
    private record AgvPoseSnapshot(String mapCode, double x, double y, double angle, String status, String currentPoint) {}
    private final Map<String, AgvPoseSnapshot> lastBroadcastPoseMap = new ConcurrentHashMap<>();
    private final AtomicLong orderHeaderId = new AtomicLong(0);
    private final AtomicLong instantActionHeaderId = new AtomicLong(0);

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AgvWebSocketHandler agvWebSocket;

    public Vda5050MqttService(ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              AgvWebSocketHandler agvWebSocket) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.agvWebSocket = agvWebSocket;
    }

    @Value("${mqtt.broker.url:tcp://mqtt:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:rcs_backend_client}")
    private String clientId;

    private MqttClient mqttClient;

    // VDA 5050 通配符訂閱主題：監聽 agv-lab 旗下所有車輛的狀態與連線生命週期
    private static final String STATE_TOPIC_WILDCARD = "uagv/v2/agv-lab/+/state";
    private static final String CONNECTION_TOPIC_WILDCARD = "uagv/v2/agv-lab/+/connection";

    // ROS 2 模擬器車隊生命周期同步主題 (獨立於 VDA 5050 標準之外)
    private static final String SIM_FLEET_CONTROL_TOPIC = "sim/fleet/control";
    private static final String SIM_FLEET_SNAPSHOT_TOPIC = "sim/fleet/snapshot";
    private volatile String lastFleetSnapshotJson;

    @PostConstruct
    public void init() {
        try {
            log.info("正在初始化 MQTT 連線至 Broker: {}, ClientID: {}", brokerUrl, clientId);
            this.mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            this.mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // 斷線自動重連
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            this.mqttClient.connect(options);
            log.info("MQTT Broker 連線成功！");

        } catch (MqttException e) {
            log.error("初始化 MQTT 連線失敗: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT 連線就緒 (重連狀態: {})，開始訂閱狀態與連線主題: {}, {}",
                reconnect, STATE_TOPIC_WILDCARD, CONNECTION_TOPIC_WILDCARD);
        try {
            // 訂閱 QoS 0 狀態流 (高頻位置資訊) 與 QoS 1 連線狀態 (含保留訊息與 LWT 遺囑)
            this.mqttClient.subscribe(STATE_TOPIC_WILDCARD, 0);
            this.mqttClient.subscribe(CONNECTION_TOPIC_WILDCARD, 1);
            // 若已有車隊快照，於連線就緒或重連時確保發佈保留快照至 Broker
            if (this.lastFleetSnapshotJson != null) {
                publishSimSnapshotJson(this.lastFleetSnapshotJson);
            }
        } catch (MqttException e) {
            log.error("訂閱主題失敗: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("與 MQTT Broker 的連線已中斷: {}", cause != null ? cause.getMessage() : "原因未知");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        try {
            log.debug("收到 MQTT 主題 [{}] 訊息: {}", topic, payload);
            if (topic.endsWith("/connection")) {
                handleConnectionMessage(payload);
                return;
            }
            handleStateMessage(payload);
        } catch (Exception e) {
            log.error("解析 VDA 5050 訊息失敗 [{}]: {}, payload: {}", topic, e.getMessage(), payload);
        }
    }

    private void handleConnectionMessage(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        String agvCode = root.path("serialNumber").asText();
        String connectionState = root.path("connectionState").asText();
        if (agvCode.isEmpty()) return;

        if ("CONNECTIONBROKEN".equals(connectionState)) {
            log.warn("車輛 [{}] 異常斷線 (MQTT LWT): {}", agvCode, connectionState);
        } else {
            log.info("收到車輛 [{}] 連線狀態更新: {}", agvCode, connectionState);
        }
    }

    private void handleStateMessage(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        String agvCode = root.path("serialNumber").asText();
        String currentPoint = root.path("lastNodeId").asText();
        String mapCode = root.path("agvPosition").path("mapId").asText();
        boolean driving = root.path("driving").asBoolean();
        double x = root.path("agvPosition").path("x").asDouble();
        double y = root.path("agvPosition").path("y").asDouble();
        double theta = root.path("agvPosition").path("theta").asDouble();

        if (agvCode.isEmpty()) return;

        // 1. 即時推送位置變化至前端 WebSocket (Delta Update，不查詢 Redis)
        broadcastPoseIfChanged(mapCode, agvCode, currentPoint, driving, x, y, theta);

        // 2. 發布狀態回報事件，交由 TrafficManagerService 處理交通申請與排隊調度
        eventPublisher.publishEvent(new AgvStateReportEvent(mapCode, agvCode, currentPoint));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 訊息發送完成回調 (QoS 1 / 2 使用)
    }

    /**
     * 將已驗證之地圖路徑快照封裝為 VDA 5050 Order 並發送至指定車輛
     *
     * @param agvCode       車輛代碼 (例如: "AGV-01")
     * @param orderId       任務代碼 (例如: "TASK-001:DELIVERY")
     * @param orderUpdateId 訂單展延序號 (新訂單自 0 起算，滑動視窗每次遞增 1)
     * @param route         由 MapGraph 解析完成之點位與邊資料
     */
    public void sendOrder(String agvCode, String orderId, long orderUpdateId, MapGraph.ResolvedPath route) {
        if (route == null || route.nodes().isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> nodes = new ArrayList<>(route.nodes().size());
            List<Map<String, Object>> edges = new ArrayList<>(route.edges().size());
            String mapId = route.mapCode();

            for (int i = 0; i < route.nodes().size(); i++) {
                PointDto p = route.nodes().get(i);
                nodes.add(Map.of(
                        "nodeId", p.pointCode(),
                        "sequenceId", i * 2,
                        "released", true,
                        "nodePosition", Map.of(
                                "x", p.x(),
                                "y", p.y(),
                                "mapId", mapId
                        )
                ));
            }

            for (int i = 0; i < route.edges().size(); i++) {
                LineDto line = route.edges().get(i);
                edges.add(Map.of(
                        "edgeId", line.lineCode(),
                        "sequenceId", i * 2 + 1,
                        "released", true,
                        "startNodeId", line.startPointCode(),
                        "endNodeId", line.endPointCode(),
                        "maxSpeed", line.maxSpeed()
                ));
            }

            Map<String, Object> orderPayload = Map.of(
                    "headerId", orderHeaderId.incrementAndGet(),
                    "timestamp", Instant.now().toString(),
                    "version", "2.0.0",
                    "manufacturer", "agv-lab",
                    "serialNumber", agvCode,
                    "orderId", orderId,
                    "orderUpdateId", orderUpdateId,
                    "nodes", nodes,
                    "edges", edges
            );

            String orderJson = objectMapper.writeValueAsString(orderPayload);

            String orderTopic = String.format("uagv/v2/agv-lab/%s/order", agvCode);
            MqttMessage message = new MqttMessage(orderJson.getBytes());
            message.setQos(1);
            this.mqttClient.publish(orderTopic, message);

            log.info("已向車輛 [{}] 發送訂單 [{} (updateId={})]: {}", agvCode, orderId, orderUpdateId, orderJson);

        } catch (Exception e) {
            log.error("向車輛 [{}] 發送訂單時失敗: {}", agvCode, e.getMessage(), e);
        }
    }

    /**
     * 向下相容方法：未指定 orderUpdateId 時預設為 0
     */
    public void sendOrder(String agvCode, String orderId, MapGraph.ResolvedPath route) {
        sendOrder(agvCode, orderId, 0L, route);
    }

    /**
     * 發送 VDA 5050 instantActions 即時動作指令至指定車輛
     *
     * @param agvCode          車輛代碼 (例如: "AGV-01")
     * @param actionType       動作類型 (startPause / stopPause / cancelOrder / initPosition)
     * @param actionId         動作唯一識別碼
     * @param actionParameters 動作參數清單 (格式: [{"key": "...", "value": ...}])
     */
    public void sendInstantAction(String agvCode, String actionType, String actionId,
                                  List<Map<String, Object>> actionParameters) {
        if (agvCode == null || agvCode.isBlank() || actionType == null || actionType.isBlank()) {
            return;
        }

        try {
            Map<String, Object> action = Map.of(
                    "actionType", actionType,
                    "actionId", (actionId != null && !actionId.isBlank())
                            ? actionId
                            : (actionType + "_" + System.currentTimeMillis()),
                    "blockingType", "HARD",
                    "actionParameters", (actionParameters != null) ? actionParameters : List.of()
            );

            Map<String, Object> payload = Map.of(
                    "headerId", instantActionHeaderId.incrementAndGet(),
                    "timestamp", Instant.now().toString(),
                    "version", "2.0.0",
                    "manufacturer", "agv-lab",
                    "serialNumber", agvCode,
                    "actions", List.of(action)
            );

            String json = objectMapper.writeValueAsString(payload);
            String topic = String.format("uagv/v2/agv-lab/%s/instantActions", agvCode);
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(1);
            this.mqttClient.publish(topic, message);

            log.info("已向車輛 [{}] 發送即時動作 [{}]: {}", agvCode, actionType, json);
        } catch (Exception e) {
            log.error("向車輛 [{}] 發送即時動作 [{}] 失敗: {}", agvCode, actionType, e.getMessage(), e);
        }
    }

    /**
     * 向指定車輛發送 cancelOrder 即時指令，要求車輛立即停止並清除當前行程
     */
    public void sendCancelOrder(String agvCode) {
        sendInstantAction(agvCode, "cancelOrder", "cancel_" + System.currentTimeMillis(), List.of());
    }

    /**
     * 向指定車輛發送 initPosition 即時指令，同步車輛在地圖上的初始座標與點位
     */
    public void sendInitPosition(String agvCode, String mapCode, String pointCode,
                                 double x, double y, double thetaRad) {
        List<Map<String, Object>> params = List.of(
                Map.of("key", "x", "value", x),
                Map.of("key", "y", "value", y),
                Map.of("key", "theta", "value", thetaRad),
                Map.of("key", "mapId", "value", mapCode != null ? mapCode : ""),
                Map.of("key", "lastNodeId", "value", pointCode != null ? pointCode : "")
        );
        sendInstantAction(agvCode, "initPosition", "initPos_" + System.currentTimeMillis(), params);
    }

    /**
     * 監聽車隊同步事件：發佈 sim/fleet/snapshot (retain=True) 與 sim/fleet/control 即時控制指令
     */
    @EventListener
    public void onSimFleetSync(SimFleetSyncEvent event) {
        try {
            Map<String, Object> snapshotPayload = Map.of(
                    "timestamp", Instant.now().toString(),
                    "agvs", event.snapshot() != null ? event.snapshot() : List.of()
            );
            String snapshotJson = objectMapper.writeValueAsString(snapshotPayload);
            this.lastFleetSnapshotJson = snapshotJson;
            publishSimSnapshotJson(snapshotJson);

            String action = event.action();
            if ("SPAWN".equals(action) || "PLACE".equals(action) || "DESTROY".equals(action)) {
                Map<String, Object> controlPayload = Map.of(
                        "action", action,
                        "agvId", event.agvId() != null ? event.agvId() : "",
                        "mapId", event.mapId() != null ? event.mapId() : "",
                        "pointId", event.pointId() != null ? event.pointId() : "",
                        "x", event.x(),
                        "y", event.y(),
                        "theta", event.theta()
                );
                String controlJson = objectMapper.writeValueAsString(controlPayload);
                if (this.mqttClient != null && this.mqttClient.isConnected()) {
                    MqttMessage msg = new MqttMessage(controlJson.getBytes());
                    msg.setQos(1);
                    this.mqttClient.publish(SIM_FLEET_CONTROL_TOPIC, msg);
                    log.info("已向模擬器發送車隊控制指令 [{}]: {}", SIM_FLEET_CONTROL_TOPIC, controlJson);
                }
            }
        } catch (Exception e) {
            log.error("發佈模擬器車隊同步訊息失敗: {}", e.getMessage(), e);
        }
    }

    private void publishSimSnapshotJson(String snapshotJson) {
        try {
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                MqttMessage msg = new MqttMessage(snapshotJson.getBytes());
                msg.setQos(1);
                msg.setRetained(true);
                this.mqttClient.publish(SIM_FLEET_SNAPSHOT_TOPIC, msg);
                log.info("已更新模擬器車隊保留快照 [{}]: {}", SIM_FLEET_SNAPSHOT_TOPIC, snapshotJson);
            }
        } catch (Exception e) {
            log.warn("發佈車隊保留快照失敗: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                this.mqttClient.disconnect();
                this.mqttClient.close();
                log.info("MQTT 連線已安全釋放");
            }
        } catch (MqttException e) {
            log.error("關閉 MQTT 連線時發生錯誤: {}", e.getMessage(), e);
        }
    }

    private void broadcastPoseIfChanged(String mapCode, String agvCode, String currentPoint,
                                        boolean driving, double x, double y, double theta) {
        double angle = Math.toDegrees(theta);
        String status = driving ? "RUNNING" : "IDLE";
        AgvPoseSnapshot current = new AgvPoseSnapshot(mapCode, x, y, angle, status, currentPoint);

        // 若座標與狀態皆未變動則不重複推送，減少網路與前端渲染負荷
        if (current.equals(lastBroadcastPoseMap.get(agvCode))) {
            return;
        }
        lastBroadcastPoseMap.put(agvCode, current);

        try {
            Map<String, Object> poseMsg = Map.of(
                    "type", "AGV_POSE",
                    "agvCode", agvCode,
                    "currentMapCode", mapCode,
                    "posX", x,
                    "posY", y,
                    "angle", angle,
                    "status", status,
                    "currentPoint", currentPoint
            );
            agvWebSocket.broadcast(objectMapper.writeValueAsString(poseMsg));
        } catch (Exception e) {
            log.warn("廣播 AGV_POSE 失敗 [{}]: {}", agvCode, e.getMessage());
        }
    }
}

