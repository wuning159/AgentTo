package com.agentto.rag.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常映射验证：HTTP 协议层错误（方法不支持、资源不存在）必须返回 405/404，
 * 请求体/参数类客户端错误必须返回 400/415，绝不落入兜底处理器返回 500。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void methodNotSupportedReturns405() {
        HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException("GET");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().message()).contains("GET");
    }

    @Test
    void missingResourceReturns404() {
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET, "/api/admin/does-not-exist", null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void malformedJsonBodyReturns400() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error", null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void unsupportedMediaTypeReturns415() {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ApiResponse<Void>> response = handler.handleMediaTypeNotSupported(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void missingRequiredParameterReturns400() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("page", "int");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParameter(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void illegalArgumentReturns400() {
        IllegalArgumentException exception = new IllegalArgumentException("所有者应用不存在: 99");
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(exception,
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().message()).contains("所有者应用不存在");
    }
}
