package com.sigma.ai.evaluation.api.dto;

import com.sigma.ai.evaluation.types.exception.BusinessException;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 标准化错误响应 DTO，由 GlobalExceptionHandler 统一构建返回。
 */
@Data
@Builder
public class ErrorResponse {

    /** 6 位业务异常码 */
    private String code;

    /** 面向前端的提示消息（仅包含预定义消息，不含内部 cause 细节） */
    private String message;

    /** 异常发生时间戳 */
    private Instant timestamp;

    /**
     * 从 {@link BusinessException} 构建 ErrorResponse，
     * message 取 {@link com.sigma.ai.evaluation.types.ErrorCode} 枚举的预定义消息。
     *
     * @param e 业务异常
     * @return ErrorResponse
     */
    public static ErrorResponse of(BusinessException e) {
        return ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getErrorCode().getMessage())
                .timestamp(Instant.now())
                .build();
    }
}
