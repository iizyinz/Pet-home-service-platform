package com.example.backend.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "预约下单请求")
public class CreateOrderRequest {

    @NotNull(message = "宠物不能为空")
    @Schema(description = "宠物档案ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long petId;

    @NotNull(message = "服务项目不能为空")
    @Schema(description = "服务项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serviceItemId;

    @NotNull(message = "服务人员不能为空")
    @Schema(description = "服务人员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long staffId;

    @NotNull(message = "上门地址不能为空")
    @Schema(description = "上门地址ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long addressId;

    @NotNull(message = "预约时间不能为空")
    @Future(message = "预约时间必须晚于当前时间")
    @Schema(description = "预约服务时间", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-10-20 14:00:00")
    private LocalDateTime appointmentTime;

    @Size(max = 255)
    @Schema(description = "备注")
    private String remark;
}
