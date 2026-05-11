package com.datn.finrisk.web.websocket;

import com.datn.finrisk.application.dtos.EmotionAIResponse;
import com.datn.finrisk.core.services.RiskEvaluationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Component
public class LiveEmotionWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private RiskEvaluationService riskEvaluationService;
    
    private final ObjectMapper mapper = new ObjectMapper();

    // ← THÊM HÀM NÀY
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🔌 [WS] Client kết nối thành công! Session ID: " + session.getId());
    }

    // ← THÊM HÀM NÀY
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        System.out.println("🔌 [WS] Client ngắt kết nối! Session ID: " + session.getId() + " | Lý do: " + status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            System.out.println("📡 [WEBSOCKET] Nhận được 1 Frame từ React, độ dài: " + message.getPayloadLength() + " bytes");
            JsonNode jsonMessage = mapper.readTree(message.getPayload());
            String base64Frame = jsonMessage.get("frame").asText();

            EmotionAIResponse aiRes = riskEvaluationService.detectEmotionAsync(base64Frame).get();

            Map<String, String> response = new HashMap<>();
            if (aiRes != null) {
                response.put("emotion", aiRes.getEmotion());
                response.put("status", "SUCCESS");
                System.out.println("🔥 [WEBSOCKET] AI phán đoán là: " + aiRes.getEmotion());
            } else {
                response.put("emotion", "UNKNOWN");
                response.put("status", "SUCCESS");
            }

            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));

        } catch (Exception e) {
            System.err.println("❌ Lỗi luồng WebSocket: " + e.getMessage());
            e.printStackTrace(); // ← thêm để thấy full stack trace
        }
    }
}