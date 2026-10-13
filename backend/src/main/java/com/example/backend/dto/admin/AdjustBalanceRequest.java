package com.example.backend.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "管理员调账请求（写 ADJUST 流水 + 审计）")
public class AdjustBalanceRequest {

    @NotNull(message = "目标用户不能为空")
    @Schema(description = "目标用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotNull(message = "金额不能为空")
    @Schema(description = "调整金额（正为增加，负为扣减）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "10.00")
    private BigDecimal amount;

    @Size(max = 255)
    @Schema(description = "调整原因（必填建议）")
    private String remark;
}
