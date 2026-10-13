package com.example.backend.controller;

import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.LoginUser;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.order.CancelRequest;
import com.example.backend.dto.order.CreateOrderRequest;
import com.example.backend.dto.order.FinishRequest;
import com.example.backend.dto.order.OrderDetailVO;
import com.example.backend.dto.order.OrderListVO;
import com.example.backend.dto.order.ProgressRequest;
import com.example.backend.entity.OrderProgress;
import com.example.backend.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务订单：下单、状态机动作、进度反馈。
 * 角色约束：cancel/confirm 仅订单用户；accept/reject/start/finish/progress 仅接单服务师。
 */
@Tag(name = "服务订单", description = "下单与订单状态机（RESTful 动作用子资源动作路径）")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "预约下单", description = "校验余额并原子扣款、写 CONSUME 流水，生成 SO 单号，状态=待确认")
    @PostMapping
    public Result<OrderListVO> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.create(UserContext.requireUserId(), request));
    }

    @Operation(summary = "我的订单列表", description = "USER 看我下的单，STAFF 看我接的单；status 筛选（ALL 表示全部）")
    @GetMapping
    public Result<PageResult<OrderListVO>> list(
            @Parameter(description = "状态筛选", example = "PENDING_CONFIRM")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.pageMyOrders(UserContext.require(), status, page, size));
    }

    @Operation(summary = "订单详情", description = "完整信息快照 + 进度 + 未读标记 + 可执行动作；越权 403")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id, UserContext.require()));
    }

    @Operation(summary = "用户取消订单", description = "仅待确认/已确认可取消；退款并写 REFUND 流水，通知服务师")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestBody(required = false) CancelRequest request) {
        String reason = request == null ? null : request.getReason();
        orderService.cancel(UserContext.requireUserId(), id, reason);
        return Result.ok();
    }

    @Operation(summary = "服务师接单", description = "待确认 -> 已确认；仅本人订单（越权 403，非法流转 2002）")
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        orderService.accept(UserContext.requireStaff().getId(), id);
        return Result.ok();
    }

    @Operation(summary = "服务师拒单", description = "待确认 -> 已取消；退款 + 通知用户")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) CancelRequest request) {
        orderService.reject(UserContext.requireStaff().getId(), id,
                request == null ? null : request.getReason());
        return Result.ok();
    }

    @Operation(summary = "开始服务", description = "已确认 -> 服务中，记录开始时间")
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        orderService.start(UserContext.requireStaff().getId(), id);
        return Result.ok();
    }

    @Operation(summary = "标记完成", description = "服务中 -> 待确认完成，录入实际服务开始/结束时间")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id,
                               @RequestBody(required = false) FinishRequest request) {
        orderService.finish(UserContext.requireStaff().getId(), id,
                request == null ? new FinishRequest() : request);
        return Result.ok();
    }

    @Operation(summary = "用户确认完成", description = "待确认完成 -> 已完成，重算服务师信誉，开放评价")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "发布服务进度", description = "仅服务中订单；标题+图片+服务时间，推送通知给用户")
    @PostMapping("/{id}/progress")
    public Result<OrderProgress> publishProgress(@PathVariable Long id,
                                                 @Valid @RequestBody ProgressRequest request) {
        return Result.ok(orderService.publishProgress(UserContext.requireStaff().getId(), id, request));
    }

    @Operation(summary = "查看服务进度", description = "订单主人查看即标记已读并记录阅读时间（未读归零）")
    @GetMapping("/{id}/progress")
    public Result<List<OrderProgress>> listProgress(@PathVariable Long id) {
        return Result.ok(orderService.listProgress(id, UserContext.require()));
    }
}
