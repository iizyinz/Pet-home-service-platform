package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 服务过程反馈（进度） */
@Data
@TableName("order_progress")
@Schema(description = "服务过程反馈")
public class OrderProgress {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "图片 JSON 数组")
    private String images;

    @Schema(description = "服务时间/说明")
    private String serviceTime;

    @Schema(description = "发布人ID")
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
