package com.example.backend.service.order;

import com.example.backend.enums.OrderAction;
import com.example.backend.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 订单状态机：集中管理 PRD 第四章状态转移表。
 * 所有状态变更必须经过本类校验，禁止散点修改 status 字段。
 * <p>
 * 转移表：
 * PENDING_CONFIRM + accept->CONFIRMED / reject|cancel|timeout->CANCELLED
 * CONFIRMED      + start->SERVING / cancel->CANCELLED
 * SERVING        + feedback->SERVING(不改状态) / finish->PENDING_SETTLE
 * PENDING_SETTLE + confirm|auto_confirm->COMPLETED
 * COMPLETED / CANCELLED 终态
 */
@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Map<OrderAction, OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

    public OrderStateMachine() {
        Map<OrderAction, OrderStatus> pending = new EnumMap<>(OrderAction.class);
        pending.put(OrderAction.ACCEPT, OrderStatus.CONFIRMED);
        pending.put(OrderAction.REJECT, OrderStatus.CANCELLED);
        pending.put(OrderAction.CANCEL, OrderStatus.CANCELLED);
        pending.put(OrderAction.TIMEOUT, OrderStatus.CANCELLED);
        transitions.put(OrderStatus.PENDING_CONFIRM, pending);

        Map<OrderAction, OrderStatus> confirmed = new EnumMap<>(OrderAction.class);
        confirmed.put(OrderAction.START, OrderStatus.SERVING);
        confirmed.put(OrderAction.CANCEL, OrderStatus.CANCELLED);
        transitions.put(OrderStatus.CONFIRMED, confirmed);

        Map<OrderAction, OrderStatus> serving = new EnumMap<>(OrderAction.class);
        serving.put(OrderAction.FEEDBACK, OrderStatus.SERVING); // 不改状态，仅校验
        serving.put(OrderAction.FINISH, OrderStatus.PENDING_SETTLE);
        transitions.put(OrderStatus.SERVING, serving);

        Map<OrderAction, OrderStatus> pendingSettle = new EnumMap<>(OrderAction.class);
        pendingSettle.put(OrderAction.CONFIRM, OrderStatus.COMPLETED);
        pendingSettle.put(OrderAction.AUTO_CONFIRM, OrderStatus.COMPLETED);
        transitions.put(OrderStatus.PENDING_SETTLE, pendingSettle);

        transitions.put(OrderStatus.COMPLETED, new EnumMap<>(OrderAction.class));
        transitions.put(OrderStatus.CANCELLED, new EnumMap<>(OrderAction.class));
    }

    /** 校验 (当前状态, 动作) 是否合法，返回目标状态 */
    public OrderStatus apply(OrderStatus from, OrderAction action) {
        return next(from, action).orElseThrow(() ->
                new com.example.backend.common.error.BizException(
                        com.example.backend.common.error.ErrorCode.ORDER_STATUS_ILLEGAL));
    }

    public Optional<OrderStatus> next(OrderStatus from, OrderAction action) {
        if (from == null) {
            return Optional.empty();
        }
        Map<OrderAction, OrderStatus> map = transitions.get(from);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(action));
    }

    public boolean canApply(OrderStatus from, OrderAction action) {
        return next(from, action).isPresent();
    }
}
