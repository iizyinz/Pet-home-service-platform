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

/** 宠物档案 */
@Data
@TableName("pet")
@Schema(description = "宠物档案")
public class Pet {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "主人用户ID")
    private Long userId;

    @Schema(description = "宠物昵称", example = "咪咪")
    private String name;

    @Schema(description = "品种", example = "布偶猫")
    private String breed;

    @Schema(description = "年龄描述", example = "3岁")
    private String age;

    @Schema(description = "体重(kg)")
    private BigDecimal weightKg;

    @Schema(description = "健康信息")
    private String healthInfo;

    @Schema(description = "特殊需求")
    private String specialNeed;

    @Schema(description = "头像")
    private String avatar;

    @TableLogic
    @Schema(description = "逻辑删除", hidden = true)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间", hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间", hidden = true)
    private LocalDateTime updatedAt;
}
