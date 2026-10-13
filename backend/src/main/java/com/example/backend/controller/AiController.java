package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.ai.AiChatRequest;
import com.example.backend.entity.AiConversation;
import com.example.backend.entity.AiMessage;
import com.example.backend.service.ai.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 宠物顾问：会话管理 + SSE 流式问答
 */
@Tag(name = "AI 宠物问答", description = "会话管理与 SSE 流式问答（DeepSeek/OpenAI 兼容）")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "新建 AI 会话")
    @PostMapping("/conversations")
    public Result<AiConversation> createConversation() {
        return Result.ok(aiService.createConversation(UserContext.requireUserId()));
    }

    @Operation(summary = "我的 AI 会话列表", description = "按更新时间倒序")
    @GetMapping("/conversations")
    public Result<List<AiConversation>> conversations() {
        return Result.ok(aiService.listConversations(UserContext.requireUserId()));
    }

    @Operation(summary = "会话历史消息", description = "role: system/user/assistant")
    @GetMapping("/conversations/{id}/messages")
    public Result<List<AiMessage>> messages(@PathVariable Long id) {
        return Result.ok(aiService.listMessages(UserContext.requireUserId(), id));
    }

    @Operation(summary = "提问（SSE 流式）",
            description = "Content-Type: text/event-stream；事件 message=增量、done=完成、error=失败（失败不写入会话）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request) {
        return aiService.chat(UserContext.requireUserId(), request);
    }

    @Operation(summary = "快捷问题", description = "首页 AI 顾问快捷入口")
    @GetMapping("/quick-questions")
    public Result<List<Map<String, String>>> quickQuestions() {
        return Result.ok(List.of(
                Map.of("title", "如何给猫咪洗澡", "question", "如何给猫咪洗澡？详细步骤和注意事项"),
                Map.of("title", "狗狗不吃东西怎么办", "question", "狗狗突然不吃东西怎么办？"),
                Map.of("title", "疫苗时间表", "question", "幼犬幼猫的疫苗接种时间表是什么？"),
                Map.of("title", "定点排便训练", "question", "如何对狗狗进行定点排便训练？")));
    }
}
