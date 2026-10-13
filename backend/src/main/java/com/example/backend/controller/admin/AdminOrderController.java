package com.example.backend.controller.admin;

import com.example.backend.common.audit.AuditLog;
import com.example.backend.common.api.Result;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.order.CancelRequest;
import com.example.backend.dto.order.OrderDetailVO;
import com.example.backend.dto.order.OrderListVO;
import com.example.backend.common.api.PageResult;
import com.example.backend.service.finance.BalanceService;
import com.example.backend.service.notice.NoticeService;
import com.example.backend.service.order.OrderService;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.mapper.ServiceOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-订单管理：全量查询、详情、运营干预（关闭并退款，写审计）
 */
@Tag(name = "管理端/订单", description = "仅 ADMIN：订单查询与运营干预（审计）")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final ServiceOrderMapper orderMapper;
    private final BalanceService balanceService;
    private final NoticeService noticeService;

    @Operation(summary = "订单分页", description = "支持状态/用户/服务师筛选")
    @GetMapping
    public Result<PageResult<OrderListVO>> page(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "服务师ID") @RequestParam(required = false) Long staffId,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageAll(status, userId, staffId, page, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id, com.example.backend.common.context.UserContext.require()));
    }

    @Operation(summary = "运营干预：关闭订单并退款",
            description = "仅未完成订单可干预；退款写 REFUND 流水，通知双方，记录审计")
    @PostMapping("/{id}/close")
    @AuditLog(action = "order.close.refund", target = "order")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> closeAndRefund(@PathVariable Long id,
                                       @RequestBody(required = false) CancelRequest request) {
        ServiceOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        String status = order.getStatus();
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "订单已终态，无法干预");
        }
        ServiceOrder upd = new ServiceOrder();
        upd.setId(id);
        upd.setStatus("CANCELLED");
        upd.setCancelReason(request != null && request.getReason() != null
                ? "运营干预：" + request.getReason() : "运营干预关闭");
        int rows = orderMapper.update(upd,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ServiceOrder>lambdaUpdate()
                        .eq(ServiceOrder::getId, id)
                        .ne(ServiceOrder::getStatus, "CANCELLED")
                        .ne(ServiceOrder::getStatus, "COMPLETED"));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        // 已扣款才退款：PENDING_CONFIRM/CONFIRMED/SERVING/PENDING_SETTLE 均已扣款
        balanceService.refund(order.getUserId(), order.getAmount(), order.getId(),
                "运营干预退款 " + order.getOrderNo());
        noticeService.send(order.getUserId(), "ORDER", "订单已被平台关闭",
                "订单 " + order.getOrderNo() + " 已关闭，款项已退回余额", order.getId());
        noticeService.send(order.getStaffId(), "ORDER", "订单已被平台关闭",
                "订单 " + order.getOrderNo() + " 已被运营关闭", order.getId());
        return Result.ok();
    }
}
