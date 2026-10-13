package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.audit.AuditLog;
import com.example.backend.entity.BalanceRecord;
import com.example.backend.entity.Notice;
import com.example.backend.entity.OperationLog;
import com.example.backend.entity.Pet;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.BalanceRecordMapper;
import com.example.backend.mapper.NoticeMapper;
import com.example.backend.mapper.OperationLogMapper;
import com.example.backend.mapper.PetMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.finance.BalanceService;
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

/**
 * 管理端-运营数据：余额流水、操作日志、通知、宠物档案
 */
@Tag(name = "管理端/运营数据", description = "仅 ADMIN：流水、审计日志、通知、宠物档案")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final BalanceService balanceService;
    private final OperationLogMapper operationLogMapper;
    private final NoticeMapper noticeMapper;
    private final PetMapper petMapper;
    private final SysUserMapper userMapper;
    private final com.example.backend.service.notice.NoticeService noticeService;

    @Operation(summary = "余额流水分页", description = "全量资金流水（消费/充值/退款/调账），用于对账审计")
    @GetMapping("/balance-records")
    public Result<PageResult<BalanceRecord>> balanceRecords(
            @Parameter(description = "类型 CONSUME/RECHARGE/REFUND/ADJUST") @RequestParam(required = false) String type,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(balanceService.pageAll(page, size, type, userId));
    }

    @Operation(summary = "操作日志分页", description = "管理端关键操作审计（操作人/动作/对象/IP/结果）")
    @GetMapping("/operation-logs")
    public Result<PageResult<OperationLog>> operationLogs(
            @Parameter(description = "动作") @RequestParam(required = false) String action,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<OperationLog>lambdaQuery()
                        .like(action != null && !action.isBlank(), OperationLog::getAction, action)
                        .orderByDesc(OperationLog::getId));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @Operation(summary = "通知分页（可补发）", description = "查看全量站内通知")
    @GetMapping("/notices")
    public Result<PageResult<Notice>> notices(
            @Parameter(description = "接收人ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<Notice> result = noticeMapper.selectPage(new Page<>(page, size),
                Wrappers.<Notice>lambdaQuery()
                        .eq(userId != null, Notice::getUserId, userId)
                        .orderByDesc(Notice::getId));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @Operation(summary = "补发通知", description = "向指定用户手动补发一条站内通知（审计）")
    @PostMapping("/notices/resend")
    @AuditLog(action = "notice.resend", target = "notice")
    public Result<Void> resendNotice(@RequestParam Long userId,
                                     @RequestParam String title,
                                     @RequestParam(required = false) String content) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.ok();
        }
        noticeService.send(userId, "SYSTEM", title, content, null);
        return Result.ok();
    }

    @Operation(summary = "宠物档案分页", description = "全量宠物档案，支持按主人筛选")
    @GetMapping("/pets")
    public Result<PageResult<Pet>> pets(
            @Parameter(description = "主人用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<Pet> result = petMapper.selectPage(new Page<>(page, size),
                Wrappers.<Pet>lambdaQuery()
                        .eq(userId != null, Pet::getUserId, userId)
                        .orderByDesc(Pet::getId));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @Operation(summary = "删除宠物档案（逻辑删除，审计）")
    @PostMapping("/pets/{id}/delete")
    @AuditLog(action = "pet.delete", target = "pet")
    public Result<Void> deletePet(@PathVariable Long id) {
        petMapper.deleteById(id);
        return Result.ok();
    }
}
