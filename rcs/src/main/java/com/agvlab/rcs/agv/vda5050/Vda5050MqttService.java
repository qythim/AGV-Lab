package com.agvlab.rcs.agv.vda5050;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.agvlab.rcs.traffic.reservation.service.ReservationService;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateRequest;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;
import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.map.service.MapService;
import com.agvlab.rcs.map.dto.MapDataDto.PointDto;

/**
 * VDA 5050 MQTT 通訊服務 (Spring Boot 與 Mosquitto 橋接核心)
 * 職責：
 * 1. 於系統啟動時建立與 MQTT Broker 的長連線
 * 2. 訂閱所有車輛的 VDA 5050 State 狀態主題 (uagv/v2/agv-lab/+/state)
 * 3. 提供向車輛發送 VDA 5050 Order 訂單的方法
 */
@Service
public class Vda5050MqttService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(Vda5050MqttService.class);
    private final Map<String, List<String>> activePathMap = new ConcurrentHashMap<>();
    private final Map<String, String> lastPointMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCheckTimeMap= new ConcurrentHashMap<>();
    private final Map<String, List<String>> lastSentPointsMap =new ConcurrentHashMap<>();

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private final MapService mapService;
    
    public Vda5050MqttService(ReservationService reservationService, ObjectMapper objectMapper, MapService mapService) {
        this.reservationService = reservationService;
        this.objectMapper = objectMapper;
        this.mapService = mapService;
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
        try{
            log.debug("收到 MQTT 主題 [{}] 訊息: {}", topic, payload);
            // TODO: 解析 VDA 5050 State JSON
            // 1. 取得 agvCode 與當前點位 (nodePosition / lastNodeId)
            JsonNode root = objectMapper.readTree(payload);
            String agvCode = root.path("serialNumber").asText();
            String currentPoint = root.path("lastNodeId").asText();
            String mapCode = root.path("agvPosition").path("mapId").asText();
            boolean driving = root.path("driving").asBoolean();
            double x = root.path("agvPosition").path("x").asDouble();
            double y = root.path("agvPosition").path("y").asDouble();
            // 2. 調用 ReservationService.updateProgress 觸發滑動窗口與後方點位釋放

            List<String> fullpath = getActivePath(agvCode);
            if (fullpath == null || fullpath.isEmpty()) return;
            if(shouldTriggerTrafficCheck(agvCode, currentPoint)){
                handleTrafficProgress(mapCode, agvCode, currentPoint, fullpath);
            }
            
        }catch(Exception e){
            log.error("解析 VDA 5050 狀態封包失敗: {}, payload: {}", e.getMessage(), payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 訊息發送完成回調 (QoS 1 / 2 使用)
    }

        /**
     * 發送 VDA 5050 Order 訂單至指定車輛，並自動註冊該車輛的當前路徑
     *
     * @param agvCode 車輛代碼 (例如: "AGV-01")
     * @param orderId 任務代碼 (例如: "order_001")
     * @param pathPoints 導航點位清單 (例如: ["P_START", "P_N1", "P_N2"])
     */
    public void sendOrder(String agvCode, String orderId, List<String> pathPoints) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return;
        }

        // 2. 包裝JSON VDA 5050 Order
        // VDA: {"orderId": "...", "nodes": [{"nodeId": "P_START"}, {"nodeId": "P_N1"}]}
        try {
            List<Map<String, Object>> nodes = new ArrayList<>();
            MapGraph graph = mapService.getActiveGraph();
            for (String point : pathPoints) {
                PointDto p = (graph != null) ? graph.nodeByCode().get(point) : null;
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

    public void registerActivePath(String agvCode, List<String> path){
        this.activePathMap.put(agvCode, path);
    }
    public List<String> getActivePath(String agvCode){
        return activePathMap.get(agvCode);
    }

    private boolean shouldTriggerTrafficCheck(String agvCode, String currentPoint){
        long now = System.currentTimeMillis();
        long lastTime = this.lastCheckTimeMap.getOrDefault(agvCode, 0L);
        if( now - lastTime >= 500 || !currentPoint.equals( this.lastPointMap.getOrDefault(agvCode, ""))){
            this.lastCheckTimeMap.put(agvCode, now);
            this.lastPointMap.put(agvCode, currentPoint);
            return true;
        }
        return false;
    }

    private void handleTrafficProgress(String mapCode,String agvCode, String currentPoint, List<String> fullPath){
        ProgressUpdateRequest progressUpdate  = new ProgressUpdateRequest(mapCode,agvCode,currentPoint,fullPath,2);
        ProgressUpdateResponse res = reservationService.updateProgress(progressUpdate);
        if(res.canMove()==true){
            if(!res.holdingPoints().equals(lastSentPointsMap.get(agvCode))){
                lastSentPointsMap.put(agvCode, res.holdingPoints());
                sendOrder(agvCode, "order_" + agvCode, res.holdingPoints());
            }
        }
    }
}

