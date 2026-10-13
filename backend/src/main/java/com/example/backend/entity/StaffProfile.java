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

/** 服务人员档案（与 sys_user 1:1） */
@Data
@TableName("staff_profile")
@Schema(description = "服务人员档案")
public class StaffProfile {

    @Schema(description = "账号ID（主键）")
    @TableId(type = IdType.INPUT)
    private Long userId;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "从业年限", example = "3")
    private Integer experienceYears;

    @Schema(description = "擅长品种 JSON 数组")
    private String goodAtBreeds;

    @Schema(description = "服务区域ID")
    private Long areaId;

    @Schema(description = "认证状态 0 待审 1 通过 2 驳回", example = "0")
    private Integer certStatus;

    @Schema(description = "资质证书图片 JSON 数组")
    private String certImages;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "综合评分(1位小数)", example = "4.8")
    private BigDecimal avgRating;

    @Schema(description = "累计完成订单数", example = "0")
    private Integer completedCount;

    @Schema(description = "1 正常 0 下架", example = "1")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(hidden = true)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(hidden = true)
    private LocalDateTime updatedAt;
}
