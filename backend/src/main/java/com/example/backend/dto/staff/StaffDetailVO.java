package com.example.backend.dto.staff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 服务人员详情 VO（含资质、简介、可服务项目、收藏状态） */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务人员详情")
public class StaffDetailVO extends StaffListVO {

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "资质证书图片")
    private List<String> certImages;

    @Schema(description = "驳回原因（认证被驳回时）")
    private String rejectReason;

    @Schema(description = "是否已收藏", example = "false")
    private Boolean favorited;

    @Schema(description = "可提供的服务项目ID列表")
    private List<Long> itemIds;

    @Schema(description = "可提供的服务项目名称")
    private List<String> itemNames;
}
