package com.example.backend.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "提交评价请求（一单一评）")
public class ReviewRequest {

    @NotNull(message = "订单不能为空")
    @Schema(description = "已完成订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    @Schema(description = "星级 1-5", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Integer rating;

    @Size(max = 500, message = "评价内容最多 500 字")
    @Schema(description = "文字内容")
    private String content;

    @Schema(description = "图片列表（可选）")
    private List<String> images;
}
