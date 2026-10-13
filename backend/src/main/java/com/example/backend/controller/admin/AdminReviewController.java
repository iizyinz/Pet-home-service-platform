package com.example.backend.controller.admin;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.audit.AuditLog;
import com.example.backend.entity.Review;
import com.example.backend.service.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-评价管理：查询、隐藏违规评价（触发信誉重算，写审计）
 */
@Tag(name = "管理端/评价", description = "仅 ADMIN：评价查询与违规隐藏（审计）")
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "评价分页", description = "支持服务师/用户/显示状态筛选")
    @GetMapping
    public Result<PageResult<Review>> page(
            @Parameter(description = "服务师ID") @RequestParam(required = false) Long staffId,
            @Parameter(description = "评价人ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "1 显示 0 隐藏") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.pageAll(staffId, userId, status, page, size));
    }

    @Operation(summary = "隐藏/恢复评价", description = "status=0 隐藏违规评价并触发服务师信誉重算")
    @PutMapping("/{id}/status")
    @AuditLog(action = "review.status", target = "review")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Parameter(description = "1 显示 0 隐藏", required = true)
                                     @RequestParam int status) {
        reviewService.updateStatus(id, status);
        return Result.ok();
    }
}
