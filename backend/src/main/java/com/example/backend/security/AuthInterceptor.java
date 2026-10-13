package com.example.backend.security;

import com.example.backend.common.api.Result;
import com.example.backend.common.context.LoginUser;
import com.example.backend.common.context.UserContext;
import com.example.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 鉴权拦截器：
 * 1) 白名单放行；2) 校验 Authorization: Bearer <token>；3) /api/admin/** 仅 ADMIN；
 * 4) 将登录者写入 UserContext，请求结束清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /** 免登录白名单：认证、验证码、公开内容、静态资源、API 文档 */
    public static final List<String> WHITE_LIST = List.of(
            "/api/auth/",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/",
            "/uploads/",
            "/ws/"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                return true;
            }
        }

        String auth = request.getHeader("Authorization");
        String token = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }
        LoginUser user = token == null ? null : jwtUtil.parse(token);
        if (user == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
            return false;
        }
        // 垂直越权防护：管理端接口仅限 ADMIN
        if (path.startsWith("/api/admin") && !user.isAdmin()) {
            write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
            return false;
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void write(HttpServletResponse response, int status, ErrorCode errorCode) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode.getCode(), errorCode.getMessage())));
    }
}
