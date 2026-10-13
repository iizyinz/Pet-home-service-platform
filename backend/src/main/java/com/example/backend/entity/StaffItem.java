package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 服务人员-可服务项目关联 */
@Data
@TableName("staff_item")
@Schema(description = "服务人员可服务项目")
public class StaffItem {

    @TableId(type = IdType.AUTO)
    @Schema(hidden = true)
    private Long id;

    @Schema(description = "服务人员ID")
    private Long staffId;

    @Schema(description = "服务项目ID")
    private Long itemId;

    @Schema(hidden = true)
    private LocalDateTime createdAt;
}
