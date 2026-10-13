package com.example.backend.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "服务人员资质审核请求")
public class CertAuditRequest {

    @NotNull(message = "审核结果不能为空")
    @Min(value = 1, message = "仅支持 1 通过 / 2 驳回")
    @Max(value = 2)
    @Schema(description = "1 已通过 2 已驳回", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer certStatus;

    @Size(max = 255)
    @Schema(description = "驳回原因（驳回时必填）")
    private String rejectReason;
}
