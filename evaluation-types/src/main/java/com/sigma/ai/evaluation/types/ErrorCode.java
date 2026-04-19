package com.sigma.ai.evaluation.types;

import lombok.Getter;

/**
 * 全局业务异常码枚举。
 *
 * <p>编码规则：6 位数字，格式 {@code TTSSSS}。
 * <ul>
 *   <li>TT（前 2 位）：异常类型大类，以 10 为步长递增</li>
 *   <li>SSSS（后 4 位）：该类型下的具体异常序号，从 0001 开始递增</li>
 * </ul>
 *
 * <p>类型大类：
 * <ul>
 *   <li>10 - 参数校验</li>
 *   <li>20 - 资源不存在</li>
 *   <li>30 - Git 操作</li>
 *   <li>40 - 索引任务</li>
 *   <li>50 - 外部存储</li>
 *   <li>60 - 外部 API 调用</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    // 10xxxx 参数校验
    REPO_ID_EMPTY("100001", "repoId 不能为空"),
    IMPACT_PARAM_INCOMPLETE("100002", "changedMethodIds 和 changedTypeNames 至少提供一个"),
    COMMIT_HASH_EMPTY("100003", "commitHash 不能为空"),
    TASK_TYPE_INVALID("100004", "taskType 不合法"),
    AI_CONTEXT_NO_INPUT("100005", "至少需要提供 commitHash、显式变更种子或 semanticQueries 之一"),
    FILE_PATH_EMPTY("100006", "filePath 不能为空"),
    FILE_PATH_OUTSIDE_REPO("100007", "filePath 必须位于该仓库 localPath 目录之下"),
    FILE_LINE_RANGE_INVALID("100009", "startLine/endLine 行号范围不合法"),
    TEXT_SEARCH_QUERY_EMPTY("100010", "文本搜索关键字 query 不能为空"),
    TEXT_SEARCH_REGEX_INVALID("100011", "正则表达式不合法"),

    // 20xxxx 资源不存在
    REPOSITORY_NOT_FOUND("200001", "仓库未注册"),
    GIT_BRANCH_NOT_FOUND("200002", "Git 分支不存在"),
    INDEX_TASK_NOT_FOUND("200003", "索引任务不存在"),
    FOLDER_CREATE_FAIL("200004", "文件夹创建失败"),
    REPO_FILE_NOT_FOUND("200005", "仓库内指定路径不存在或不是可读文件"),
    REPO_LOCAL_WORKSPACE_NOT_FOUND("200006", "仓库 localPath 在运行环境中不存在或不是目录"),

    // 30xxxx Git 操作
    GIT_CLONE_FAILED("300001", "Git clone 失败"),
    GIT_PULL_FAILED("300002", "Git pull 失败"),
    GIT_DIFF_FAILED("300003", "Git diff 失败"),
    GIT_HEAD_HASH_FAILED("300004", "获取 HEAD commit hash 失败"),

    // 40xxxx 索引任务
    FULL_INDEX_FAILED("400001", "全量索引执行失败"),
    INCREMENTAL_INDEX_FAILED("400002", "增量索引执行失败"),
    FILE_READ_FAILED("400003", "Java 文件读取失败"),
    AST_PARSE_FAILED("400004", "Java 文件语法解析失败"),
    TEXT_SEARCH_WALK_FAILED("400005", "代码文本搜索遍历时失败"),

    // 50xxxx 外部存储
    NEO4J_WRITE_ERROR("500001", "Neo4j 写入异常"),
    NEO4J_QUERY_ERROR("500002", "Neo4j 查询异常"),
    MILVUS_WRITE_ERROR("500003", "Milvus 向量写入异常"),
    MILVUS_DELETE_ERROR("500004", "Milvus 向量删除异常"),
    MILVUS_SEARCH_ERROR("500005", "Milvus 语义检索异常"),

    // 60xxxx 外部 API
    EMBEDDING_API_FAILED("600001", "Embedding API 调用失败"),
    EMBEDDING_API_BAD_STATUS("600002", "Embedding API 返回异常状态码"),
    EMBEDDING_RESULT_MISMATCH("600003", "Embedding 向量结果数量不匹配"),

    // 兜底
    UNKNOWN_ERROR("999999", "系统内部错误");

    /** 6 位异常码 */
    private final String code;

    /** 面向前端的默认提示消息 */
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
