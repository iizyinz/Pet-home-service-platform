package com.example.backend.dto.order;

import com.example.backend.entity.OrderProgress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 订单详情 VO：客户与服务信息完整快照 + 进度 + 阅读状态 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单详情")
public class OrderDetailVO extends OrderListVO {

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话（脱敏）")
    private String contactPhone;

    @Schema(description = "服务地址（省市区+详细）")
    private String address;

    @Schema(description = "服务项目名称快照")
    private String itemName;

    @Schema(description = "服务时长(分钟)")
    private Integer durationMinutes;

    @Schema(description = "进度列表（新到旧）")
    private List<OrderProgress> progressList;

    @Schema(description = "是否存在未读进度", example = "false")
    private Boolean progressUnread;

    @Schema(description = "本人是否已评价", example = "false")
    private Boolean reviewed;

    @Schema(description = "可执行动作（前端按钮控制）")
    private List<String> availableActions;
}
