package com.example.backend.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "取消/拒单请求")
public class CancelRequest {

    @Size(max = 255)
    @Schema(description = "取消/拒单原因")
    private String reason;
}
