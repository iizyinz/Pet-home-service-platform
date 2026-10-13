package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账号（用户/服务人员/管理员统一）
 */
@Data
@TableName("sys_user")
@Schema(description = "账号")
public class SysUser {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "登录名", example = "zhangsan")
    private String username;

    /** BCrypt 密码，序列化时不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "密码（BCrypt，仅写入）", writeOnly = true)
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "角色 USER/STAFF/ADMIN", example = "USER")
    private String role;

    @Schema(description = "余额（元）")
    private BigDecimal balance;

    @Schema(description = "1 正常 0 冻结", example = "1")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
