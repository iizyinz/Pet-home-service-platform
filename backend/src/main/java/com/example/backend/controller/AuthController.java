package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.dto.auth.LoginRequest;
import com.example.backend.dto.auth.LoginResponse;
import com.example.backend.dto.auth.RegisterRequest;
import com.example.backend.service.auth.CaptchaService;
import com.example.backend.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证与验证码（免登录白名单）
 */
@Tag(name = "认证", description = "登录、注册、图形验证码")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @Operation(summary = "获取图形验证码", description = "返回 captchaKey 与 base64 图片，5 分钟有效")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.ok(captchaService.generate());
    }

    @Operation(summary = "账号密码登录", description = "校验验证码后签发 JWT，返回 token 与用户信息")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "注册", description = "新用户注册，角色 USER/STAFF；STAFF 自动创建待审核服务人员档案")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok();
    }
}
