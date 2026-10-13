package com.example.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3-64")
    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 64)
    @Schema(description = "昵称")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "USER|STAFF", message = "角色仅支持 USER/STAFF")
    @Schema(description = "注册角色 USER/STAFF（管理员由系统预置）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "USER")
    private String role;

    @Schema(description = "验证码 key")
    private String captchaKey;

    @Schema(description = "验证码文本")
    private String captchaCode;
}
