package com.agvlab.rcs.agv.vda5050;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.agvlab.rcs.map.dto.MapDataDto.PointDto;
import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.map.service.MapService;
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

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MapService mapService;
    private final AgvWebSocketHandler agvWebSocket;

    public Vda5050MqttService(ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              MapService mapService,
                              AgvWebSocketHandler agvWebSocket) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.mapService = mapService;
        this.agvWebSocket = agvWebSocket;
    }

    @Value("${mqtt.broker.url:tcp://mqtt:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:rcs_backend_client}")
    private String clientId;

    private MqttClient mqttClient;

    // VDA 5050 通配符訂閱主題：監聽 agv-lab 旗下所有車輛的狀態
    private static final String STATE_TOPIC_WILDCARD = "uagv/v2/agv-lab/+/state";

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
        log.info("MQTT 連線就緒 (重連狀態: {})，開始訂閱車輛狀態主題: {}", reconnect, STATE_TOPIC_WILDCARD);
        try {
            // 訂閱 QoS 0 狀態流 (高頻位置資訊)
            this.mqttClient.subscribe(STATE_TOPIC_WILDCARD, 0);
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

        } catch (Exception e) {
            log.error("解析 VDA 5050 狀態封包失敗: {}, payload: {}", e.getMessage(), payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 訊息發送完成回調 (QoS 1 / 2 使用)
    }

    /**
     * 發送 VDA 5050 Order 訂單至指定車輛
     *
     * @param agvCode    車輛代碼 (例如: "AGV-01")
     * @param orderId    任務代碼 (例如: "order_001")
     * @param pathPoints 導航點位清單 (例如: ["P_START", "P_N1"])
     */
    public void sendOrder(String agvCode, String orderId, List<String> pathPoints) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> nodes = new ArrayList<>();
            MapGraph graph = mapService.getActiveGraph();
            for (String point : pathPoints) {
                PointDto p = (graph != null) ? graph.nodeByCode().get(point) : null;
                if (p == null) {
                    throw new IllegalArgumentException("找不到點位座標: " + point);
                }
                nodes.add(Map.of(
                        "nodeId", point,
                        "nodePosition", Map.of("x", p.x(), "y", p.y())
                ));
            }

            Map<String, Object> orderPayload = Map.of(
                    "orderId", orderId,
                    "nodes", nodes
            );

            String orderJson = objectMapper.writeValueAsString(orderPayload);

            String orderTopic = String.format("uagv/v2/agv-lab/%s/order", agvCode);
            MqttMessage message = new MqttMessage(orderJson.getBytes());
            message.setQos(1);
            this.mqttClient.publish(orderTopic, message);

            log.info("已向車輛 [{}] 發送訂單 [{}]: {}", agvCode, orderId, orderJson);

        } catch (Exception e) {
            log.error("向車輛 [{}] 發送訂單時失敗: {}", agvCode, e.getMessage(), e);
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

