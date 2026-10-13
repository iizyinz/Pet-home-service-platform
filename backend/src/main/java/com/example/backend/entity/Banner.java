package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 首页轮播图 */
@Data
@TableName("banner")
@Schema(description = "轮播图")
public class Banner {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "跳转地址")
    private String link;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "1 启用 0 停用", example = "1")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
