package com.example.backend.common.exception;

import com.example.backend.common.api.Result;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：所有异常统一输出 { code, message, data }
 * 约定：401/403 返回对应 HTTP 状态码（越权防护验收项），业务错误返回 200 + 非 0 code
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletResponse response) {
        if (e.getCode() == ErrorCode.UNAUTHORIZED.getCode()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        } else if (e.getCode() == ErrorCode.FORBIDDEN.getCode()
                || e.getCode() == ErrorCode.ORDER_NO_ACCESS.getCode()
                || e.getCode() == ErrorCode.CHAT_FORBIDDEN.getCode()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
        } else if (e.getCode() == ErrorCode.NOT_FOUND.getCode()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody JSON 字段校验 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.BAD_REQUEST.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /** 表单绑定校验 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.BAD_REQUEST.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /** @RequestParam / @PathVariable 校验 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMismatch(Exception e) {
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求参数格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /** 兜底异常：记录完整堆栈（含 traceId 定位），对外不泄露细节 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常: ", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
