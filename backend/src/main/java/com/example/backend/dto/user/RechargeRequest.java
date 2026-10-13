package com.example.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "余额充值请求（模拟支付，不接真实网关）")
public class RechargeRequest {

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额最小 0.01 元")
    @DecimalMax(value = "10000", message = "单次充值上限 10000 元")
    @Schema(description = "充值金额（元）", requiredMode = Schema.RequiredMode.REQUIRED, example = "100.00")
    private BigDecimal amount;
}
