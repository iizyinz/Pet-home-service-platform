package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.user.ProfileUpdateRequest;
import com.example.backend.dto.user.RechargeRequest;
import com.example.backend.entity.BalanceRecord;
import com.example.backend.entity.SysUser;
import com.example.backend.service.finance.BalanceService;
import com.example.backend.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 个人中心：个人信息、余额、流水、充值
 */
@Tag(name = "个人中心", description = "个人信息与余额（需登录）")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BalanceService balanceService;

    @Operation(summary = "获取当前用户信息", description = "需登录；返回昵称、头像、脱敏手机号、角色等")
    @GetMapping("/profile")
    public Result<SysUser> profile() {
        return Result.ok(userService.profile(UserContext.requireUserId()));
    }

    @Operation(summary = "更新个人信息", description = "可修改昵称/头像/手机号；同时传原密码+新密码则修改密码")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        userService.updateProfile(UserContext.requireUserId(), request);
        return Result.ok();
    }

    @Operation(summary = "查询余额", description = "返回当前账户余额（元）")
    @GetMapping("/balance")
    public Result<Map<String, BigDecimal>> balance() {
        return Result.ok(Map.of("balance", balanceService.getBalance(UserContext.requireUserId())));
    }

    @Operation(summary = "余额流水分页", description = "消费/充值/退款记录，含变动金额与变动后余额快照")
    @GetMapping("/balance/records")
    public Result<PageResult<BalanceRecord>> records(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(balanceService.pageRecords(UserContext.requireUserId(), page, size));
    }

    @Operation(summary = "余额充值（模拟）", description = "平台内部余额充值，不接真实支付网关；写 RECHARGE 流水")
    @PostMapping("/balance/recharge")
    public Result<Map<String, BigDecimal>> recharge(@Valid @RequestBody RechargeRequest request) {
        balanceService.recharge(UserContext.requireUserId(), request.getAmount());
        return Result.ok(Map.of("balance", balanceService.getBalance(UserContext.requireUserId())));
    }
}
