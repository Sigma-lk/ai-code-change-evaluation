package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;
import lombok.Getter;

/**
 * 业务异常基类，所有自定义业务异常均继承此类。
 *
 * <p>持有 {@link ErrorCode} 枚举引用，提供统一的 6 位异常码和默认消息。
 * 子类按异常大类划分，便于全局异常处理器按类型映射 HTTP 状态码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
    }

    /**
     * @return 6 位业务异常码
     */
    public String getCode() {
        return errorCode.getCode();
    }

}
