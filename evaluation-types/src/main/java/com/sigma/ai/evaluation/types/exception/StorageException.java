package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * 外部存储异常（50xxxx），表示 Neo4j / Milvus 等存储组件的读写异常。
 */
public class StorageException extends BusinessException {

    public StorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public StorageException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode, detailMessage, cause);
    }

    public static StorageException neo4jWriteError(Throwable cause) {
        return new StorageException(ErrorCode.NEO4J_WRITE_ERROR, cause);
    }

    public static StorageException neo4jQueryError(Throwable cause) {
        return new StorageException(ErrorCode.NEO4J_QUERY_ERROR, cause);
    }

    public static StorageException milvusWriteError(Throwable cause) {
        return new StorageException(ErrorCode.MILVUS_WRITE_ERROR, cause);
    }

    public static StorageException milvusDeleteError(Throwable cause) {
        return new StorageException(ErrorCode.MILVUS_DELETE_ERROR, cause);
    }

    public static StorageException milvusSearchError(Throwable cause) {
        return new StorageException(ErrorCode.MILVUS_SEARCH_ERROR, cause);
    }
}
