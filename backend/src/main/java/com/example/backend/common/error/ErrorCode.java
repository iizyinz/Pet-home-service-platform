package com.example.backend.common.error;

import lombok.Getter;

/**
 * 全局错误码字典。
 * 0 成功；1xxx 认证账号；2xxx 业务/交易；3xxx 智能/实时；4xxx 文件/系统
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),

    // ---- 通用 ----
    BAD_REQUEST(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),

    // ---- 认证与账号 ----
    CAPTCHA_INVALID(1001, "验证码错误或已过期"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    USER_NOT_FOUND(1003, "用户不存在"),
    PASSWORD_WRONG(1004, "用户名或密码错误"),
    USER_FROZEN(1005, "账号已被冻结，请联系管理员"),
    OLD_PASSWORD_WRONG(1006, "原密码错误"),
    LOGIN_LIMIT(1007, "登录失败次数过多，请稍后再试"),

    // ---- 交易 / 订单 ----
    BALANCE_INSUFFICIENT(2001, "余额不足，请先充值"),
    ORDER_STATUS_ILLEGAL(2002, "订单当前状态不允许此操作"),
    ORDER_NOT_FOUND(2003, "订单不存在"),
    ORDER_NO_ACCESS(2004, "无权操作该订单"),
    REVIEW_DUPLICATE(2005, "该订单已评价，不可重复提交"),
    REVIEW_NOT_ALLOWED(2006, "仅已完成订单可评价"),
    REVIEW_RATING_INVALID(2007, "评分须为 1-5 星"),
    STAFF_NOT_CERTIFIED(2008, "服务人员尚未通过认证，暂不能接单"),
    STAFF_NOT_FOUND(2009, "服务人员不存在"),
    PET_NOT_FOUND(2010, "宠物档案不存在"),
    ADDRESS_NOT_FOUND(2011, "上门地址不存在"),
    ITEM_NOT_FOUND(2012, "服务项目不存在"),
    ITEM_OFF_SHELF(2013, "服务项目已下架"),
    DATA_EXISTS(2014, "数据已存在"),
    DATA_IN_USE(2015, "存在关联数据，无法删除"),
    APPOINTMENT_INVALID(2016, "预约时间必须晚于当前时间"),
    PROGRESS_ONLY_SERVING(2017, "仅服务中订单可发布进度"),
    RECHARGE_INVALID(2018, "充值金额必须大于 0"),
    TARGET_USER_NOT_FOUND(2019, "目标用户不存在"),

    // ---- 智能 / 实时 ----
    AI_ERROR(3001, "AI 服务暂时不可用，请稍后重试"),
    AI_CONVERSATION_NOT_FOUND(3002, "AI 会话不存在"),
    CHAT_FORBIDDEN(3003, "不能与该用户私聊"),
    CONVERSATION_NOT_FOUND(3004, "会话不存在"),

    // ---- 文件 / 系统 ----
    FILE_INVALID(4001, "文件类型或大小不合法"),
    UPLOAD_FAILED(4002, "文件上传失败"),
    CAPTCHA_GENERATE_FAILED(4003, "验证码生成失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
