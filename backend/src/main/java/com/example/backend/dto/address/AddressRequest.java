package com.example.backend.dto.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "上门地址请求")
public class AddressRequest {

    @NotBlank(message = "联系人不能为空")
    @Size(max = 64)
    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contactPhone;

    @Size(max = 128)
    @Schema(description = "省市区", example = "北京市/朝阳区")
    private String region;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255)
    @Schema(description = "详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String detail;

    @Schema(description = "1 默认地址", example = "0")
    private Integer isDefault;
}
