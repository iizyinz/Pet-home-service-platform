package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.entity.Announcement;
import com.example.backend.entity.Banner;
import com.example.backend.service.master.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页内容：轮播图、系统公告（小程序端展示）
 */
@Tag(name = "首页内容", description = "轮播图与公告（小程序首页/公告入口）")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @Operation(summary = "启用轮播图", description = "按排序返回启用状态的轮播图")
    @GetMapping("/banners")
    public Result<List<Banner>> banners() {
        return Result.ok(contentService.listBanners());
    }

    @Operation(summary = "公告列表", description = "仅返回已发布公告，新到旧")
    @GetMapping("/announcements")
    public Result<PageResult<Announcement>> announcements(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pageAnnouncements(page, size));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/announcements/{id}")
    public Result<Announcement> announcementDetail(@PathVariable Long id) {
        return Result.ok(contentService.getAnnouncement(id));
    }
}
