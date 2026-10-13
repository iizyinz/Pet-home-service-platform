package com.example.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 分页结果：{ list, total, page, size }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "页码（从 1 开始）", example = "1")
    private int page;

    @Schema(description = "每页条数", example = "10")
    private int size;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}
