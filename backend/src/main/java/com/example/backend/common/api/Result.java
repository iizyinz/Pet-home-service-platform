package com.example.backend.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体：{ code, message, data }，code=0 表示成功
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应体")
public class Result<T> {

    @Schema(description = "业务状态码，0 成功，非 0 失败", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "success")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
