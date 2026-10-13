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

/** 余额流水 */
@Data
@TableName("balance_record")
@Schema(description = "余额流水")
public class BalanceRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "归属用户ID")
    private Long userId;

    @Schema(description = "类型 CONSUME/RECHARGE/REFUND/ADJUST", example = "CONSUME")
    private String type;

    @Schema(description = "变动金额（正入负出）")
    private BigDecimal amount;

    @Schema(description = "变动后余额快照")
    private BigDecimal balanceAfter;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "渠道（预留真实支付）", example = "BALANCE")
    private String channel;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
