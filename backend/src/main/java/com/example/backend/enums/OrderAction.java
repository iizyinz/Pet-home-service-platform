package com.example.backend.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单动作（PRD 第四章 4.2 状态转移表）
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "订单动作")
public enum OrderAction {

    ACCEPT("accept", "接单"),
    REJECT("reject", "拒单"),
    CANCEL("cancel", "取消"),
    TIMEOUT("timeout", "超时未接"),
    START("start", "开始服务"),
    FEEDBACK("feedback", "发布进度"),
    FINISH("finish", "标记完成"),
    CONFIRM("confirm", "确认完成"),
    AUTO_CONFIRM("auto_confirm", "超时自动确认");

    private final String code;
    private final String label;
}
