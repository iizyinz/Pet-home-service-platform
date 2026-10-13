package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知 */
@Data
@TableName("notice")
@Schema(description = "站内通知")
public class Notice {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "接收人ID")
    private Long userId;

    @Schema(description = "类型 ORDER/REVIEW/CERT/SYSTEM", example = "ORDER")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "业务ID（订单等）")
    private Long bizId;

    @Schema(description = "1 已读", example = "0")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
