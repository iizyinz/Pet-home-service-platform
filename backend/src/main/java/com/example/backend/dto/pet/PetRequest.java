package com.example.backend.dto.pet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "宠物档案请求")
public class PetRequest {

    @NotBlank(message = "宠物昵称不能为空")
    @Size(max = 64)
    @Schema(description = "宠物昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "咪咪")
    private String name;

    @Size(max = 64)
    @Schema(description = "品种", example = "布偶猫")
    private String breed;

    @Size(max = 32)
    @Schema(description = "年龄描述", example = "3岁")
    private String age;

    @Schema(description = "体重(kg)", example = "4.5")
    private BigDecimal weightKg;

    @Size(max = 500)
    @Schema(description = "健康信息")
    private String healthInfo;

    @Size(max = 500)
    @Schema(description = "特殊需求")
    private String specialNeed;

    @Schema(description = "头像")
    private String avatar;
}
