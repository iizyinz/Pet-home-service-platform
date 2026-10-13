package com.example.backend.common.context;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录者（从 JWT 解析后放入 UserContext）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录用户上下文信息")
public class LoginUser {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "角色 USER/STAFF/ADMIN", example = "USER")
    private String role;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isStaff() {
        return "STAFF".equals(role);
    }
}
