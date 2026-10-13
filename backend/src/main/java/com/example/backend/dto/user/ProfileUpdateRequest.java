package com.example.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新个人信息请求")
public class ProfileUpdateRequest {

    @Size(max = 64)
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = 255)
    @Schema(description = "头像地址")
    private String avatar;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "原密码（修改密码时必填）")
    private String oldPassword;

    @Schema(description = "新密码（与原密码同时提供时修改密码）")
    private String newPassword;
}
