package com.example.backend.chat;

import com.example.backend.entity.ChatMessage;
import com.example.backend.service.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 聊天 WebSocket：连接注册/注销；收到客户端消息时落库并转发给接收方。
 * 推送协议：{ type:"chat", data: ChatMessage }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final OnlineSessionManager sessionManager;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.register(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        try {
            // 客户端也可直接经 WS 发送：{ receiverId, content, msgType }
            ChatSendPayload payload = objectMapper.readValue(message.getPayload(), ChatSendPayload.class);
            if (payload.receiverId() == null || payload.content() == null) {
                return;
            }
            ChatMessage saved = chatService.send(userId, payload.receiverId(),
                    payload.content(), payload.msgType());
            // 落库后定向推送给接收方（在线）；自己的其它端也会收到回执
            String json = "{\"type\":\"chat\",\"data\":" + objectMapper.writeValueAsString(saved) + "}";
            sessionManager.sendTo(payload.receiverId(), json);
            sessionManager.sendTo(userId, json);
        } catch (Exception e) {
            log.warn("WS 消息处理失败 userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }

    /** WS 上行消息体 */
    public record ChatSendPayload(Long receiverId, String content, String msgType) {
    }
}
