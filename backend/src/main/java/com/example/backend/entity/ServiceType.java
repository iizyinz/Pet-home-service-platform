package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 服务类型（分类） */
@Data
@TableName("service_type")
@Schema(description = "服务类型")
public class ServiceType {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "类型名称", example = "洗护")
    private String name;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "1 启用 0 停用", example = "1")
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
