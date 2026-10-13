package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 服务人员收藏 */
@Data
@TableName("favorite")
@Schema(description = "收藏")
public class Favorite {

    @TableId(type = IdType.AUTO)
    @Schema(hidden = true)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "服务人员ID")
    private Long staffId;

    @Schema(hidden = true)
    private LocalDateTime createdAt;
}
