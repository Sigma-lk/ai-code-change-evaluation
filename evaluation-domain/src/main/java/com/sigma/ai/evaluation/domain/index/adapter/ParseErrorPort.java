package com.sigma.ai.evaluation.domain.index.adapter;

/**
 * 解析错误记录持久化 Port，操作 t_parse_error 表。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface ParseErrorPort {

    /**
     * 记录单个文件的解析失败信息。
     *
     * @param taskId    所属任务 ID
     * @param filePath  解析失败的文件路径
     * @param errorType 错误类型标识（如 PARSE_EXCEPTION、IO_ERROR）
     * @param errorMsg  错误详情
     */
    void record(Long taskId, String filePath, String errorType, String errorMsg);
}
