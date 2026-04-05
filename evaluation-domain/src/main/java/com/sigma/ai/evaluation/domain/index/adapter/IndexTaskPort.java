package com.sigma.ai.evaluation.domain.index.adapter;

import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.types.TaskStatus;

/**
 * 索引任务持久化 Port，操作 t_index_task 表。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface IndexTaskPort {

    /**
     * 创建新任务并返回带 id 的任务对象。
     *
     * @param task 待创建的任务
     * @return 已持久化（含 id）的任务对象
     */
    IndexTask createTask(IndexTask task);

    /**
     * 更新任务状态及完成时间/错误信息。
     *
     * @param taskId   任务 ID
     * @param status   新状态
     * @param errorMsg 失败原因（成功时传 null）
     */
    void updateTaskStatus(Long taskId, TaskStatus status, String errorMsg);

    /**
     * 根据 ID 查询任务。
     *
     * @param taskId 任务 ID
     * @return 任务对象，不存在时返回 null
     */
    IndexTask findById(Long taskId);
}
