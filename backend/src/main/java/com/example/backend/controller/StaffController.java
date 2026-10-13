package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.staff.StaffDetailVO;
import com.example.backend.dto.staff.StaffListVO;
import com.example.backend.dto.staff.StaffProfileRequest;
import com.example.backend.entity.StaffProfile;
import com.example.backend.service.review.ReviewService;
import com.example.backend.service.staff.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务人员：筛选列表、详情（含评价）、收藏、个人资料维护
 */
@Tag(name = "服务人员", description = "服务人员筛选、详情、收藏与资料维护")
@RestController
@RequestMapping("/api/staffs")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final ReviewService reviewService;

    @Operation(summary = "服务人员列表", description = "按区域 areaId、擅长品种 breed、关键词筛选；仅展示认证通过人员")
    @GetMapping
    public Result<PageResult<StaffListVO>> list(
            @Parameter(description = "服务区域ID") @RequestParam(required = false) Long areaId,
            @Parameter(description = "擅长品种") @RequestParam(required = false) String breed,
            @Parameter(description = "昵称关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(staffService.pageStaffs(areaId, breed, keyword, page, size));
    }

    @Operation(summary = "服务人员详情", description = "含资质、从业年限、评分、可服务项目、收藏状态与服务评价")
    @GetMapping("/{id}")
    public Result<StaffDetailVO> detail(@PathVariable Long id) {
        return Result.ok(staffService.getStaff(UserContext.requireUserId(), id));
    }

    @Operation(summary = "服务人员评价列表", description = "仅返回已显示的评价")
    @GetMapping("/{id}/reviews")
    public Result<PageResult<com.example.backend.entity.Review>> reviews(
            @PathVariable Long id,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.pageByStaff(id, page, size));
    }

    @Operation(summary = "收藏/取消收藏", description = "幂等切换，返回切换后是否已收藏")
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Boolean>> favorite(@PathVariable Long id) {
        boolean favorited = staffService.toggleFavorite(UserContext.requireUserId(), id);
        return Result.ok(Map.of("favorited", favorited));
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorites")
    public Result<List<StaffListVO>> favorites() {
        return Result.ok(staffService.myFavorites(UserContext.requireUserId()));
    }

    @Operation(summary = "维护我的服务人员资料", description = "仅 STAFF 本人：简介/年限/擅长品种/区域/资质/可服务项目")
    @PutMapping("/me/profile")
    public Result<Void> updateMyProfile(@Valid @RequestBody StaffProfileRequest request) {
        staffService.updateMyProfile(UserContext.requireStaff().getId(), request);
        return Result.ok();
    }

    @Operation(summary = "我的服务人员资料", description = "含评分、累计接单、认证状态")
    @GetMapping("/me/profile")
    public Result<StaffProfile> myProfile() {
        return Result.ok(staffService.myProfile(UserContext.requireStaff().getId()));
    }
}
