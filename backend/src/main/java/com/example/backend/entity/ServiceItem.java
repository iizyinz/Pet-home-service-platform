package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 服务项目 */
@Data
@TableName("service_item")
@Schema(description = "服务项目")
public class ServiceItem {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属服务类型ID")
    private Long typeId;

    @Schema(description = "项目名称", example = "上门喂猫")
    private String name;

    @Schema(description = "单价（元/次）", example = "88.00")
    private BigDecimal price;

    @Schema(description = "服务时长(分钟)", example = "60")
    private Integer durationMinutes;

    @Schema(description = "封面图")
    private String coverUrl;

    /** JSON 数组字符串，如 ["布偶猫","英短"] */
    @Schema(description = "适用品种 JSON 数组", example = "[\"布偶猫\"]")
    private String applicableBreeds;

    @Schema(description = "注意事项")
    private String notice;

    @Schema(description = "1 上架 0 下架", example = "1")
    private Integer status;

    @TableLogic
    @Schema(hidden = true)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
