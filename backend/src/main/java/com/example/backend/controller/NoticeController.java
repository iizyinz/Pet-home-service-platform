package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.entity.Notice;
import com.example.backend.service.notice.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内通知中心
 */
@Tag(name = "站内通知", description = "我的通知列表、未读数、标记已读")
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "我的通知分页", description = "新到旧")
    @GetMapping
    public Result<PageResult<Notice>> list(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(noticeService.pageMy(UserContext.requireUserId(), page, size));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", noticeService.unreadCount(UserContext.requireUserId())));
    }

    @Operation(summary = "单条标记已读")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        noticeService.markAllRead(UserContext.requireUserId());
        return Result.ok();
    }
}
