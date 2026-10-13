package com.example.backend.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "发布服务进度请求（仅服务中订单）")
public class ProgressRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128)
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "已到家开始喂食")
    private String title;

    @Schema(description = "图片地址列表（多张）")
    private List<String> images;

    @Size(max = 64)
    @Schema(description = "服务时间/说明", example = "14:00-14:30 喂食+铲屎")
    private String serviceTime;
}
