package com.example.backend.dto.staff;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "服务人员资料维护请求")
public class StaffProfileRequest {

    @Size(max = 2000)
    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "从业年限", example = "3")
    private Integer experienceYears;

    @Schema(description = "擅长品种")
    private List<String> goodAtBreeds;

    @Schema(description = "服务区域ID")
    private Long areaId;

    @Schema(description = "资质证书图片")
    private List<String> certImages;

    @Schema(description = "可提供的服务项目ID列表")
    private List<Long> itemIds;
}
