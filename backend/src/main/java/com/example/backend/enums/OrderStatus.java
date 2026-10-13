package com.example.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态机状态（PRD 第四章 4.1）
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "订单状态")
public enum OrderStatus {

    PENDING_CONFIRM("PENDING_CONFIRM", "待确认"),
    CONFIRMED("CONFIRMED", "已确认"),
    SERVING("SERVING", "服务中"),
    PENDING_SETTLE("PENDING_SETTLE", "待确认完成"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    public static OrderStatus of(String code) {
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
