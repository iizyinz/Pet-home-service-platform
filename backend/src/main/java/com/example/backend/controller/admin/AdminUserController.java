package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.audit.AuditLog;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.dto.admin.AdjustBalanceRequest;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.finance.BalanceService;
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

/**
 * 管理端-用户管理：查询、冻结/解冻、余额调整（写流水 + 审计）
 */
@Tag(name = "管理端/用户管理", description = "仅 ADMIN：查询、冻结、余额调整（审计）")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper userMapper;
    private final BalanceService balanceService;

    @Operation(summary = "用户分页查询", description = "支持关键词（用户名/昵称）与角色筛选")
    @GetMapping
    public Result<PageResult<SysUser>> page(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色 USER/STAFF/ADMIN") @RequestParam(required = false) String role,
            @Parameter(description = "状态 1 正常 0 冻结") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<SysUser> result = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                        .eq(status != null, SysUser::getStatus, status)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getNickname, keyword))
                        .orderByDesc(SysUser::getId));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @Operation(summary = "冻结/解冻用户", description = "冻结后无法登录；审计记录")
    @PutMapping("/{id}/status")
    @AuditLog(action = "user.status", target = "user")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Parameter(description = "1 正常 0 冻结", required = true)
                                     @RequestParam int status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.ok();
        }
        if ("ADMIN".equals(user.getRole()) && status == 0) {
            return Result.ok(); // 不允许冻结管理员
        }
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setStatus(status);
        userMapper.updateById(upd);
        return Result.ok();
    }

    @Operation(summary = "余额调整", description = "写 ADJUST 流水，原子加/扣款，记录审计")
    @PostMapping("/balance/adjust")
    @AuditLog(action = "user.balance.adjust", target = "user")
    public Result<Void> adjustBalance(@Valid @RequestBody AdjustBalanceRequest request) {
        balanceService.adjust(request.getUserId(), request.getAmount(),
                request.getRemark() == null ? "管理员调账" : request.getRemark());
        return Result.ok();
    }
}
