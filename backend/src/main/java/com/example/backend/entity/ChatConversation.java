package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 聊天会话（参与者对唯一） */
@Data
@TableName("chat_conversation")
@Schema(description = "聊天会话")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "参与者A（较小ID）")
    private Long userAId;

    @Schema(description = "参与者B（较大ID）")
    private Long userBId;

    @Schema(description = "最新一条摘要")
    private String lastMessage;

    @Schema(description = "最新消息时间")
    private LocalDateTime lastMessageAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
