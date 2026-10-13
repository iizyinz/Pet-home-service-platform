package com.example.backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话管理：userId -> 连接集合（支持多端），断线自动清理。
 * 消息先落库，再通过本类定向推送（PRD 8.4）。
 */
@Slf4j
@Component
public class OnlineSessionManager {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS 上线 userId={} sessions={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId);
            }
        }
        log.info("WS 下线 userId={}", userId);
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set != null && !set.isEmpty();
    }

    /** 向用户所有在线连接推送文本（失败仅记日志，不影响主流程） */
    public void sendTo(Long userId, String payload) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("WS 推送失败 userId={} err={}", userId, e.getMessage());
                }
            }
        }
    }
}
