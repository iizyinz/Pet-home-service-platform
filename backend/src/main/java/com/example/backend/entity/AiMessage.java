package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 问答消息（role: system/user/assistant） */
@Data
@TableName("ai_message")
@Schema(description = "AI 问答消息")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "角色 system/user/assistant", example = "user")
    private String role;

    @Schema(description = "内容")
    private String content;

    @Schema(hidden = true)
    private LocalDateTime createdAt;
}
