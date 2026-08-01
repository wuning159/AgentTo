package com.agentto.rag.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage(), TraceIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message, TraceIdFilter.current(request)));
    }

    /**
     * 不支持的 HTTP 方法（如对仅 POST 的端点发 GET）返回 405，而非 500。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持：" + exception.getMethod(),
                        TraceIdFilter.current(request)));
    }

    /**
     * 不存在的资源返回 404，而非 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "请求的资源不存在", TraceIdFilter.current(request)));
    }

    /**
     * 请求体 JSON 解析失败（如非法转义、格式错误）返回 400，而非 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "请求体格式错误或为空", TraceIdFilter.current(request)));
    }

    /**
     * 请求体媒体类型不支持返回 415，而非 500。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE", "不支持的 Content-Type："
                        + exception.getContentType(), TraceIdFilter.current(request)));
    }

    /**
     * 缺少必填请求参数返回 400，而非 500。
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingPathVariableException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Void>> handleMissingParameter(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "缺少必填请求参数：" + exception.getMessage(),
                        TraceIdFilter.current(request)));
    }

    /**
     * 请求参数类型不匹配（如把 abc 当数字）返回 400，而非 500。
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, BindException.class})
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", "请求参数不合法：" + exception.getMessage(),
                        TraceIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务处理失败，请根据 traceId 查看日志",
                        TraceIdFilter.current(request)));
    }
}
