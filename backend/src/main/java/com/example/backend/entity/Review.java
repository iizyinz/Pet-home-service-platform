package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 服务评价（一单一评） */
@Data
@TableName("review")
@Schema(description = "服务评价")
public class Review {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单ID（唯一，一单一评）")
    private Long orderId;

    @Schema(description = "评价人ID")
    private Long userId;

    @Schema(description = "被评服务师ID")
    private Long staffId;

    @Schema(description = "星级 1-5", example = "5")
    private Integer rating;

    @Schema(description = "文字内容")
    private String content;

    @Schema(description = "图片 JSON 数组")
    private String images;

    @Schema(description = "1 显示 0 隐藏", example = "1")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
