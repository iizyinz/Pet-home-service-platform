package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.audit.AuditLog;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.admin.CertAuditRequest;
import com.example.backend.entity.StaffProfile;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.StaffProfileMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.notice.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端-服务人员管理：资料查询、资质审核、上下架
 */
@Tag(name = "管理端/服务人员", description = "仅 ADMIN：资料、资质审核、上下架（审计）")
@RestController
@RequestMapping("/api/admin/staffs")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffProfileMapper profileMapper;
    private final SysUserMapper userMapper;
    private final NoticeService noticeService;

    @Operation(summary = "服务人员分页", description = "含账号信息与认证状态，支持认证状态与昵称筛选")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            @Parameter(description = "认证状态 0 待审 1 通过 2 驳回") @RequestParam(required = false) Integer certStatus,
            @Parameter(description = "关键词（昵称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<StaffProfile> result = profileMapper.selectPage(new Page<>(page, size),
                Wrappers.<StaffProfile>lambdaQuery()
                        .eq(certStatus != null, StaffProfile::getCertStatus, certStatus)
                        .orderByDesc(StaffProfile::getUpdatedAt));
        java.util.List<Map<String, Object>> list = result.getRecords().stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", p.getUserId());
            item.put("intro", p.getIntro());
            item.put("experienceYears", p.getExperienceYears());
            item.put("areaId", p.getAreaId());
            item.put("certStatus", p.getCertStatus());
            item.put("certImages", p.getCertImages());
            item.put("rejectReason", p.getRejectReason());
            item.put("avgRating", p.getAvgRating());
            item.put("completedCount", p.getCompletedCount());
            item.put("status", p.getStatus());
            SysUser user = userMapper.selectById(p.getUserId());
            if (user != null) {
                item.put("nickname", user.getNickname());
                item.put("phone", user.getPhone());
                item.put("avatar", user.getAvatar());
                item.put("username", user.getUsername());
            }
            return item;
        }).filter(m -> keyword == null || keyword.isBlank()
                || (m.get("nickname") != null && String.valueOf(m.get("nickname")).contains(keyword)))
                .toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, size));
    }

    @Operation(summary = "资质审核", description = "1 通过 / 2 驳回（需填原因）；通过后可接单，推送通知")
    @PutMapping("/{userId}/cert")
    @AuditLog(action = "staff.cert.audit", target = "staff")
    public Result<Void> auditCert(@PathVariable Long userId,
                                  @Valid @RequestBody CertAuditRequest request) {
        StaffProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        if (request.getCertStatus() == 2
                && (request.getRejectReason() == null || request.getRejectReason().isBlank())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "驳回必须填写原因");
        }
        StaffProfile upd = new StaffProfile();
        upd.setUserId(userId);
        upd.setCertStatus(request.getCertStatus());
        upd.setRejectReason(request.getCertStatus() == 2 ? request.getRejectReason() : null);
        profileMapper.updateById(upd);

        noticeService.send(userId, "CERT",
                request.getCertStatus() == 1 ? "资质审核通过" : "资质审核未通过",
                request.getCertStatus() == 1 ? "恭喜，您已通过认证，可以开始接单"
                        : "驳回原因：" + request.getRejectReason(),
                null);
        return Result.ok();
    }

    @Operation(summary = "服务人员上/下架", description = "下架后不在用户端列表展示")
    @PutMapping("/{userId}/status")
    @AuditLog(action = "staff.status", target = "staff")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @Parameter(description = "1 正常 0 下架", required = true)
                                     @RequestParam int status) {
        StaffProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        StaffProfile upd = new StaffProfile();
        upd.setUserId(userId);
        upd.setStatus(status);
        profileMapper.updateById(upd);
        return Result.ok();
    }

    @Operation(summary = "服务人员详情")
    @GetMapping("/{userId}")
    public Result<StaffProfile> detail(@PathVariable Long userId) {
        StaffProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        return Result.ok(profile);
    }
}
