package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.review.ReviewRequest;
import com.example.backend.entity.Review;
import com.example.backend.service.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价：提交（仅已完成订单、一单一评）、查询
 */
@Tag(name = "服务评价", description = "提交评价与评价查询")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价",
            description = "仅本人已完成订单可评；一单一评；保存后重算服务师信誉并推送通知（重复 2005，非已完成 2006）")
    @PostMapping
    public Result<Review> submit(@Valid @RequestBody ReviewRequest request) {
        return Result.ok(reviewService.submit(UserContext.requireUserId(), request));
    }

    @Operation(summary = "评价列表", description = "按 staffId 查服务师评价，或查我发出的评价（my=1）")
    @GetMapping
    public Result<PageResult<Review>> list(
            @Parameter(description = "服务人员ID") @RequestParam(required = false) Long staffId,
            @Parameter(description = "1 查我发出的评价", example = "0")
            @RequestParam(required = false, defaultValue = "0") Integer my,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        if (my != null && my == 1) {
            return Result.ok(reviewService.pageMy(UserContext.requireUserId(), page, size));
        }
        return Result.ok(reviewService.pageByStaff(staffId, page, size));
    }
}
