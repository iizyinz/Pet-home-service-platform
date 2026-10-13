package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 聊天消息 */
@Data
@TableName("chat_message")
@Schema(description = "聊天消息")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "消息类型 TEXT/IMAGE", example = "TEXT")
    private String msgType;

    @Schema(description = "1 已读", example = "0")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
