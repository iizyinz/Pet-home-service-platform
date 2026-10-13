package com.example.backend.dto.order;

import com.example.backend.entity.ServiceOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 订单列表 VO（卡片：订单号/项目/宠物/服务师/预约时间/金额） */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单列表项")
public class OrderListVO extends ServiceOrder {

    @Schema(description = "宠物昵称")
    private String petName;

    @Schema(description = "宠物品种")
    private String petBreed;

    @Schema(description = "服务师昵称")
    private String staffName;

    @Schema(description = "服务师头像")
    private String staffAvatar;

    @Schema(description = "状态中文名", example = "待确认")
    private String statusLabel;

    @Schema(description = "下单用户昵称（管理员/服务师视角）")
    private String ownerName;

    @Schema(description = "未读进度数")
    private Long unreadProgress;
}
