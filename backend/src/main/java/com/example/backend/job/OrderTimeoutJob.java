package com.example.backend.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单定时任务（PRD 3.2 / 8.2）：
 * 1) PENDING_CONFIRM 超过 N 小时未接单 -> 自动取消 + 退款（幂等）
 * 2) PENDING_SETTLE 超过 N 小时未确认 -> 自动完成，开放评价（幂等）
 * 每单独立事务（通过 OrderService 代理方法调用），单条失败不影响其它。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final ServiceOrderMapper orderMapper;
    private final OrderService orderService;

    @Value("${app.order.accept-timeout-hours:24}")
    private int acceptTimeoutHours;

    @Value("${app.order.confirm-timeout-hours:72}")
    private int confirmTimeoutHours;

    /** 每 5 分钟扫描一次 */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void run() {
        try {
            timeoutAccept();
            autoConfirm();
        } catch (Exception e) {
            log.error("订单定时任务异常", e);
        }
    }

    private void timeoutAccept() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(acceptTimeoutHours);
        List<ServiceOrder> list = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStatus, "PENDING_CONFIRM")
                .lt(ServiceOrder::getCreatedAt, deadline)
                .last("LIMIT 200"));
        for (ServiceOrder order : list) {
            try {
                orderService.timeoutAccept(order.getId());
                log.info("超时未接自动取消 orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("超时取消失败 orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
        }
    }

    private void autoConfirm() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(confirmTimeoutHours);
        List<ServiceOrder> list = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStatus, "PENDING_SETTLE")
                .lt(ServiceOrder::getUpdatedAt, deadline)
                .last("LIMIT 200"));
        for (ServiceOrder order : list) {
            try {
                orderService.autoConfirm(order.getId());
                log.info("超时未确认自动完成 orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.warn("自动完成失败 orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
        }
    }
}
