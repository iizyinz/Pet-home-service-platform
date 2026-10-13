package com.example.backend.dto.auth;

import com.example.backend.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT token")
    private String token;

    @Schema(description = "过期时间戳（毫秒）")
    private Long expiresAt;

    @Schema(description = "用户信息")
    private SysUser user;

    public static LoginResponse of(String token, Long expiresAt, SysUser user) {
        // 出参脱敏：手机号 138****5678
        if (user != null && user.getPhone() != null && user.getPhone().length() == 11) {
            user.setPhone(user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7));
        }
        return LoginResponse.builder().token(token).expiresAt(expiresAt).user(user).build();
    }
}
