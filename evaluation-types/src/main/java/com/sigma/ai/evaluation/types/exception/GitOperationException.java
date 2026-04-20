package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * Git 操作异常（30xxxx），表示与 Git 仓库交互时发生的错误。
 */
public class GitOperationException extends BusinessException {

    public GitOperationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public GitOperationException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }

    public static GitOperationException cloneFailed(Throwable cause) {
        return new GitOperationException(ErrorCode.GIT_CLONE_FAILED, cause);
    }

    public static GitOperationException pullFailed(Throwable cause) {
        return new GitOperationException(ErrorCode.GIT_PULL_FAILED, cause);
    }

    public static GitOperationException diffFailed(Throwable cause) {
        return new GitOperationException(ErrorCode.GIT_DIFF_FAILED, cause);
    }

    public static GitOperationException headHashFailed(Throwable cause) {
        return new GitOperationException(ErrorCode.GIT_HEAD_HASH_FAILED, cause);
    }

    public static GitOperationException fetchFailed(Throwable cause) {
        return new GitOperationException(ErrorCode.GIT_FETCH_FAILED, cause);
    }
}
