package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * 索引任务异常（40xxxx），表示全量/增量索引流程中的业务失败。
 */
public class IndexTaskException extends BusinessException {

    public IndexTaskException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public IndexTaskException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }

    public static IndexTaskException fullIndexFailed(Throwable cause) {
        return new IndexTaskException(ErrorCode.FULL_INDEX_FAILED, cause);
    }

    public static IndexTaskException incrementalIndexFailed(Throwable cause) {
        return new IndexTaskException(ErrorCode.INCREMENTAL_INDEX_FAILED, cause);
    }

    public static IndexTaskException fileReadFailed(String filePath, Throwable cause) {
        return new IndexTaskException(ErrorCode.FILE_READ_FAILED,
                ErrorCode.FILE_READ_FAILED.getMessage() + ": " + filePath, cause);
    }

    public static IndexTaskException astParseFailed(String filePath) {
        return new IndexTaskException(ErrorCode.AST_PARSE_FAILED,
                ErrorCode.AST_PARSE_FAILED.getMessage() + ": " + filePath, null);
    }
}
