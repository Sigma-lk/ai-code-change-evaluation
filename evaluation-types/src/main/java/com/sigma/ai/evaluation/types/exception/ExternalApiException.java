package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * 外部 API 调用异常（60xxxx），表示调用第三方 API 时发生的错误。
 */
public class ExternalApiException extends BusinessException {

    public ExternalApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ExternalApiException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public static ExternalApiException embeddingApiFailed(Throwable cause) {
        return new ExternalApiException(ErrorCode.EMBEDDING_API_FAILED, cause);
    }

    public static ExternalApiException embeddingApiBadStatus(String statusCode) {
        return new ExternalApiException(ErrorCode.EMBEDDING_API_BAD_STATUS,
                ErrorCode.EMBEDDING_API_BAD_STATUS.getMessage() + ": " + statusCode);
    }

    public static ExternalApiException embeddingResultMismatch(int expected, int actual) {
        return new ExternalApiException(ErrorCode.EMBEDDING_RESULT_MISMATCH,
                ErrorCode.EMBEDDING_RESULT_MISMATCH.getMessage()
                        + ": expected=" + expected + ", actual=" + actual);
    }
}
