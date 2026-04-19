package com.sigma.ai.evaluation.infrastructure.pg.adapter;

import com.sigma.ai.evaluation.domain.index.adapter.IndexTaskPort;
import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.infrastructure.pg.mapper.IndexTaskMapper;
import com.sigma.ai.evaluation.infrastructure.pg.po.IndexTaskPO;
import com.sigma.ai.evaluation.types.TaskStatus;
import com.sigma.ai.evaluation.types.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@link IndexTaskPort} 的 PostgreSQL 实现。
 */
@Component
@RequiredArgsConstructor
public class IndexTaskPortImpl implements IndexTaskPort {

    private final IndexTaskMapper indexTaskMapper;

    @Override
    public IndexTask createTask(IndexTask task) {
        IndexTaskPO po = new IndexTaskPO();
        po.setRepoId(task.getRepoId());
        po.setTaskType(task.getTaskType().name());
        po.setTriggerCommit(task.getTriggerCommit());
        po.setStatus(task.getStatus().ordinal());
        if (task.getStartedAt() != null) {
            po.setStartTime(LocalDateTime.ofInstant(task.getStartedAt(), ZoneOffset.UTC));
        }
        indexTaskMapper.insert(po);
        task.setId(po.getId());
        return task;
    }

    @Override
    public void updateTaskStatus(Long taskId, TaskStatus status, String errorMsg) {
        indexTaskMapper.updateStatus(taskId, status.ordinal(), errorMsg);
    }

    @Override
    public IndexTask findById(Long taskId) {
        IndexTaskPO po = indexTaskMapper.selectById(taskId);
        return po == null ? null : toDomain(po);
    }

    private IndexTask toDomain(IndexTaskPO po) {
        return IndexTask.builder()
                .id(po.getId())
                .repoId(po.getRepoId())
                .taskType(TaskType.valueOf(po.getTaskType()))
                .triggerCommit(po.getTriggerCommit())
                .status(TaskStatus.values()[po.getStatus()])
                .startedAt(po.getStartTime() != null
                        ? po.getStartTime().toInstant(ZoneOffset.UTC) : null)
                .finishedAt(po.getFinishTime() != null
                        ? po.getFinishTime().toInstant(ZoneOffset.UTC) : null)
                .errorMsg(po.getErrorMsg())
                .build();
    }
}
