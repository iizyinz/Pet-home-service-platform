package com.example.backend.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "标记完成请求（录入实际服务时间）")
public class FinishRequest {

    @Schema(description = "实际开始时间（缺省取开始服务时间/当前时间）",
            example = "2026-10-20 14:00:00")
    private LocalDateTime actualStartTime;

    @Schema(description = "实际完成时间（缺省当前时间）", example = "2026-10-20 15:00:00")
    private LocalDateTime actualFinishTime;
}
