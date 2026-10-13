package com.example.backend.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "AI 问答请求（SSE 流式返回）")
public class AiChatRequest {

    @Schema(description = "会话ID；为空时自动新建会话")
    private Long conversationId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000)
    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "如何给猫咪洗澡？")
    private String question;
}
