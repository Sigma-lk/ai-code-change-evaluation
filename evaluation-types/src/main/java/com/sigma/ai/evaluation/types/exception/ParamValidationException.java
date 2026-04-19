package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * 参数校验异常（10xxxx），表示请求入参不合法。
 */
public class ParamValidationException extends BusinessException {

    public ParamValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ParamValidationException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public static ParamValidationException repoIdEmpty() {
        return new ParamValidationException(ErrorCode.REPO_ID_EMPTY);
    }

    public static ParamValidationException impactParamIncomplete() {
        return new ParamValidationException(ErrorCode.IMPACT_PARAM_INCOMPLETE);
    }

    public static ParamValidationException commitHashEmpty() {
        return new ParamValidationException(ErrorCode.COMMIT_HASH_EMPTY);
    }

    public static ParamValidationException taskTypeInvalid() {
        return new ParamValidationException(ErrorCode.TASK_TYPE_INVALID);
    }

    /**
     * AI 分析上下文请求无可分析输入（无 commit、无显式种子、无语义查询）。
     */
    public static ParamValidationException aiContextNoInput() {
        return new ParamValidationException(ErrorCode.AI_CONTEXT_NO_INPUT);
    }

    public static ParamValidationException filePathEmpty() {
        return new ParamValidationException(ErrorCode.FILE_PATH_EMPTY);
    }

    public static ParamValidationException filePathOutsideRepo() {
        return new ParamValidationException(ErrorCode.FILE_PATH_OUTSIDE_REPO);
    }

    public static ParamValidationException fileLineRangeInvalid(String detail) {
        return new ParamValidationException(ErrorCode.FILE_LINE_RANGE_INVALID, detail);
    }

    public static ParamValidationException textSearchQueryEmpty() {
        return new ParamValidationException(ErrorCode.TEXT_SEARCH_QUERY_EMPTY);
    }

    public static ParamValidationException textSearchRegexInvalid(String detail) {
        return new ParamValidationException(ErrorCode.TEXT_SEARCH_REGEX_INVALID, detail);
    }
}
