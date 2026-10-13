package com.example.backend.chat;

import com.example.backend.security.JwtUtil;
import com.example.backend.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权：/ws/chat?token=<JWT>，校验失败返回 401 拒绝握手。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = null;
        for (String pair : query.split("&")) {
            if (pair.startsWith("token=")) {
                token = pair.substring(6);
            }
        }
        var user = token == null ? null : jwtUtil.parse(token);
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("userId", user.getId());
        attributes.put("role", user.getRole());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
