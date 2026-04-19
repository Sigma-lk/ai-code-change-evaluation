package com.sigma.ai.evaluation.trigger.handler;

import com.sigma.ai.evaluation.api.dto.ErrorResponse;
import com.sigma.ai.evaluation.types.ErrorCode;
import com.sigma.ai.evaluation.types.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * 全局异常处理器，将业务异常统一映射为标准化 {@link ErrorResponse} 响应。
 *
 * <p>映射规则：
 * <ul>
 *   <li>{@link ParamValidationException} -> HTTP 400</li>
 *   <li>{@link ResourceNotFoundException} -> HTTP 404</li>
 *   <li>{@link ExternalApiException} -> HTTP 502</li>
 *   <li>其余 {@link BusinessException} 子类 -> HTTP 500</li>
 *   <li>未知 {@link Exception} -> HTTP 500 + 兜底错误码 999999</li>
 * </ul>
 *
 * <p>响应体 message 只返回 {@link ErrorCode} 枚举中预定义的默认消息，
 * 不暴露底层 cause / 异常栈等内部实现细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ParamValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ParamValidationException e) {
        log.warn("参数校验异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(e));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e) {
        log.warn("资源不存在: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(e));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(ExternalApiException e) {
        log.error("外部 API 调用异常: code={}", e.getCode(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(e));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.error("业务异常: code={}", e.getCode(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(e));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("未知系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .code(ErrorCode.UNKNOWN_ERROR.getCode())
                        .message(ErrorCode.UNKNOWN_ERROR.getMessage())
                        .timestamp(Instant.now())
                        .build());
    }
}
