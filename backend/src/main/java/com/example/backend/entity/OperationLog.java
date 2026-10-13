package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端操作日志（审计）
 */
@Data
@TableName("operation_log")
@Schema(description = "操作日志")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "对象")
    private String target;

    @Schema(description = "详情")
    private String detail;

    @Schema(description = "结果 SUCCESS/FAIL")
    private String result;

    @Schema(description = "来源IP")
    private String ip;

    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
