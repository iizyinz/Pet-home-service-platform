package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.entity.ChatMessage;
import com.example.backend.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 即时聊天 REST 接口（WebSocket 推送：/ws/chat?token=）
 */
@Tag(name = "即时聊天", description = "会话列表、历史消息、发送消息（配合 WebSocket /ws/chat）")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Data
    public static class SendRequest {
        @NotNull(message = "接收人不能为空")
        @Parameter(description = "接收者用户ID", required = true)
        private Long receiverId;
        @NotBlank(message = "内容不能为空")
        @Parameter(description = "消息内容", required = true)
        private String content;
        @Parameter(description = "类型 TEXT/IMAGE", example = "TEXT")
        private String msgType;
    }

    @Operation(summary = "会话列表", description = "每个聊天对象的最新一条消息、时间、未读数、在线状态")
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations() {
        return Result.ok(chatService.conversationList(UserContext.requireUserId()));
    }

    @Operation(summary = "联系人信息", description = "私信入口展示对方昵称/头像/角色/在线状态")
    @GetMapping("/peer")
    public Result<Map<String, Object>> peer(@Parameter(description = "对方用户ID", required = true)
                                            @RequestParam Long peerId) {
        return Result.ok(chatService.peerInfo(UserContext.requireUserId(), peerId));
    }

    @Operation(summary = "历史消息分页", description = "拉取即把发给我的消息标记已读，未读数归零")
    @GetMapping("/messages")
    public Result<PageResult<ChatMessage>> messages(
            @Parameter(description = "会话ID", required = true) @RequestParam Long convId,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "20") @RequestParam(defaultValue = "20") int size) {
        return Result.ok(chatService.pageMessages(UserContext.requireUserId(), convId, page, size));
    }

    @Operation(summary = "发送消息", description = "先落库，接收方在线则 WebSocket 定向推送，离线留存")
    @PostMapping("/messages")
    public Result<ChatMessage> send(@RequestBody SendRequest request) {
        return Result.ok(chatService.send(UserContext.requireUserId(),
                request.getReceiverId(), request.getContent(), request.getMsgType()));
    }
}
