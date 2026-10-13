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

/** 服务区域 */
@Data
@TableName("service_area")
@Schema(description = "服务区域")
public class ServiceArea {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "区域名称", example = "朝阳区")
    private String name;

    @Schema(description = "区域编码")
    private String code;

    @Schema(description = "父级ID，0 顶级", example = "0")
    private Long parentId;

    @Schema(description = "层级 1 市 2 区", example = "1")
    private Integer level;

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
