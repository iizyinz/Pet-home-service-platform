package com.example.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64)
    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码 key（GET /api/auth/captcha 返回）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "A3B2")
    private String captchaCode;

    @Schema(description = "记住我（延长 token 有效期标记，前端持久化用）", example = "false")
    private Boolean rememberMe;
}
