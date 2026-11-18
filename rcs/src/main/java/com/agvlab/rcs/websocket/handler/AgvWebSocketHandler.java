package com.agvlab.rcs.websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.List; 
import java.util.Map;
import java.util.HashMap;

import com.agvlab.rcs.traffic.reservation.service.RedisService;
import com.agvlab.rcs.agv.service.AgvService;
import com.agvlab.rcs.agv.dto.AgvResponse; 
/**
 * 原生 WebSocket 連線管理與即時廣播處理器
 * 職責：維護前端連線會話 (Session)，並提供向單一或全體前端推送 JSON 訊息的方法
 */
@Component
public class AgvWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgvWebSocketHandler.class);

    // 執行緒安全集合，儲存所有目前開啟中的前端 WebSocket 會話
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final AgvService agvService;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    public AgvWebSocketHandler(AgvService agvService, RedisService redisService,ObjectMapper objectMapper) { 
        this.agvService = agvService; 
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket 前端已連線: sessionId={}, 目前連線數={}", session.getId(), sessions.size());
        // 連線建立後立即推送 FULL_SNAPSHOT 初始全量快照
        sendFullSnapshot(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到來自前端 [{}] 的訊息: {}", session.getId(), payload);
        try {
            JsonNode root = objectMapper.readTree(payload);
            if ("RESYNC".equals(root.path("type").asText())) {
                sendFullSnapshot(session);
            }
        } catch (Exception e) {
            // 忽略無效 JSON 訊息，避免中斷既有 WebSocket 會話
            log.warn("解析前端 WebSocket 訊息失敗 [{}]: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket 前端已斷線: sessionId={}, 原因={}, 剩餘連線數={}",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("WebSocket 傳輸異常，已移除會話 [{}]: {}",
                session.getId(), exception != null ? exception.getMessage() : "未知錯誤");
    }

    /**
     * 向所有連線中的前端廣播 JSON 字串
     */
    public void broadcast(String payload) {
        for (WebSocketSession session : sessions) {
            sendToSession(session, payload);
        }
    }

    /**
     * 安全傳送文字訊息至指定單一會話 (避免多執行緒同時寫入同一 Session 發生衝突)
     */
    public void sendToSession(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            // 鎖定單一 Session，防止 MQTT 與 Reservation 執行緒併發寫入導致異常
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (IOException e) {
            log.warn("推送 WebSocket 訊息失敗，移除會話 [{}]: {}", session.getId(), e.getMessage());
            sessions.remove(session);
        }
    }

    private void sendFullSnapshot(WebSocketSession session) {
        try {
            List<AgvResponse> agvs = agvService.getAllAgvs();
            Map<String, Set<String>> locks = new HashMap<>();
            for (AgvResponse agv : agvs) {
                Set<String> pts = redisService.getAgvPoints(agv.agvCode());
                if (pts != null && !pts.isEmpty()) {
                    locks.put(agv.agvCode(), pts);
                }
            }

            Map<String, Object> snapshot = Map.of(
                    "type", "FULL_SNAPSHOT",
                    "agvs", agvs,
                    "locks", locks
            );
            String json = objectMapper.writeValueAsString(snapshot);
            sendToSession(session, json);
        } catch (Exception e) {
            log.error("發送 FULL_SNAPSHOT 失敗 [{}]: {}", session.getId(), e.getMessage(), e);
        }
    }
}

