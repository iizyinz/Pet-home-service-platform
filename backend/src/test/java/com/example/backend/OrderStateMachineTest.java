package com.example.backend;

import com.example.backend.common.error.BizException;
import com.example.backend.enums.OrderAction;
import com.example.backend.enums.OrderStatus;
import com.example.backend.service.order.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机单测：严格对照 PRD 第四章 4.2 状态转移表（含 AC-03 非法流转拒绝）。
 * 纯内存逻辑，无需数据库。
 */
class OrderStateMachineTest {

    private OrderStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new OrderStateMachine();
    }

    @Test
    @DisplayName("AC-03: COMPLETED 状态下执行 start 必须被拒绝，状态不变")
    void completedCannotStart() {
        BizException ex = assertThrows(BizException.class,
                () -> machine.apply(OrderStatus.COMPLETED, OrderAction.START));
        assertEquals(2002, ex.getCode());
        assertEquals("订单当前状态不允许此操作", ex.getMessage());
    }

    @Test
    @DisplayName("正向主链路全通过")
    void happyPath() {
        assertEquals(OrderStatus.CONFIRMED,
                machine.apply(OrderStatus.PENDING_CONFIRM, OrderAction.ACCEPT));
        assertEquals(OrderStatus.SERVING,
                machine.apply(OrderStatus.CONFIRMED, OrderAction.START));
        assertEquals(OrderStatus.SERVING,
                machine.apply(OrderStatus.SERVING, OrderAction.FEEDBACK));
        assertEquals(OrderStatus.PENDING_SETTLE,
                machine.apply(OrderStatus.SERVING, OrderAction.FINISH));
        assertEquals(OrderStatus.COMPLETED,
                machine.apply(OrderStatus.PENDING_SETTLE, OrderAction.CONFIRM));
        assertEquals(OrderStatus.COMPLETED,
                machine.apply(OrderStatus.PENDING_SETTLE, OrderAction.AUTO_CONFIRM));
    }

    @Test
    @DisplayName("逆向流程：取消/拒单/超时 -> CANCELLED")
    void cancelPaths() {
        assertEquals(OrderStatus.CANCELLED,
                machine.apply(OrderStatus.PENDING_CONFIRM, OrderAction.CANCEL));
        assertEquals(OrderStatus.CANCELLED,
                machine.apply(OrderStatus.PENDING_CONFIRM, OrderAction.REJECT));
        assertEquals(OrderStatus.CANCELLED,
                machine.apply(OrderStatus.PENDING_CONFIRM, OrderAction.TIMEOUT));
        assertEquals(OrderStatus.CANCELLED,
                machine.apply(OrderStatus.CONFIRMED, OrderAction.CANCEL));
    }

    @Test
    @DisplayName("终态不可再流转，且状态转移表外的组合全部非法")
    void illegalTransitions() {
        // 终态
        assertFalse(machine.canApply(OrderStatus.COMPLETED, OrderAction.CONFIRM));
        assertFalse(machine.canApply(OrderStatus.CANCELLED, OrderAction.ACCEPT));
        // 未开始不能完成
        assertFalse(machine.canApply(OrderStatus.PENDING_CONFIRM, OrderAction.FINISH));
        // 未接单不能开始
        assertFalse(machine.canApply(OrderStatus.PENDING_CONFIRM, OrderAction.START));
        // 服务中不能确认完成
        assertFalse(machine.canApply(OrderStatus.SERVING, OrderAction.CONFIRM));
        // 待确认完成不能再次接单
        assertFalse(machine.canApply(OrderStatus.PENDING_SETTLE, OrderAction.ACCEPT));
        // 非服务中不能发布进度
        assertFalse(machine.canApply(OrderStatus.CONFIRMED, OrderAction.FEEDBACK));
    }

    @Test
    @DisplayName("非法流转抛 2002 业务错误")
    void illegalThrowsBizError() {
        assertThrows(BizException.class,
                () -> machine.apply(OrderStatus.SERVING, OrderAction.CANCEL));
    }
}
