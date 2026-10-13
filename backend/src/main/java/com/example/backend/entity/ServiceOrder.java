package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 服务订单（状态机核心实体） */
@Data
@TableName("service_order")
@Schema(description = "服务订单")
public class ServiceOrder {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单号（SO 开头）", example = "SO20261012000001")
    private String orderNo;

    @Schema(description = "下单用户ID")
    private Long userId;

    @Schema(description = "接单服务人员ID（待确认时为空）")
    private Long staffId;

    @Schema(description = "宠物ID")
    private Long petId;

    @Schema(description = "服务项目ID")
    private Long serviceItemId;

    @Schema(description = "上门地址ID")
    private Long addressId;

    @Schema(description = "服务项目名称快照")
    private String itemName;

    @Schema(description = "订单金额（元）")
    private BigDecimal amount;

    @Schema(description = "订单状态", example = "PENDING_CONFIRM")
    private String status;

    @Schema(description = "预约服务时间")
    private LocalDateTime appointmentTime;

    @Schema(description = "实际开始时间")
    private LocalDateTime startTime;

    @Schema(description = "服务师标记完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "用户确认完成时间")
    private LocalDateTime confirmTime;

    @Schema(description = "取消/拒单原因")
    private String cancelReason;

    @Schema(description = "用户备注")
    private String remark;

    @Schema(description = "查看进度最后阅读时间（订单粒度）")
    private LocalDateTime progressReadAt;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
