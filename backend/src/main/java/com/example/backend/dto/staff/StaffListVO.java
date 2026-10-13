package com.example.backend.dto.staff;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 服务人员列表 VO */
@Data
@Schema(description = "服务人员列表项")
public class StaffListVO {

    @Schema(description = "服务人员（账号）ID")
    private Long id;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "手机号（脱敏）")
    private String phone;

    @Schema(description = "服务区域ID")
    private Long areaId;

    @Schema(description = "服务区域名称")
    private String areaName;

    @Schema(description = "从业年限", example = "3")
    private Integer experienceYears;

    @Schema(description = "综合评分", example = "4.8")
    private BigDecimal avgRating;

    @Schema(description = "累计完成订单数", example = "56")
    private Integer completedCount;

    @Schema(description = "认证状态 0 待审 1 通过 2 驳回", example = "1")
    private Integer certStatus;

    @Schema(description = "擅长品种")
    private List<String> goodAtBreeds;
}
