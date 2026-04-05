package com.sigma.ai.evaluation.infrastructure.pg.adapter;

import com.sigma.ai.evaluation.domain.index.adapter.IndexTaskPort;
import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.infrastructure.pg.mapper.IndexTaskMapper;
import com.sigma.ai.evaluation.infrastructure.pg.po.IndexTaskPO;
import com.sigma.ai.evaluation.types.TaskStatus;
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
        po.setStatus(task.getStatus().name());
        po.setStartedAt(toLocalDateTime(task.getStartedAt() != null
                ? task.getStartedAt().toEpochMilli() : System.currentTimeMillis()));
        indexTaskMapper.insert(po);
        task.setId(po.getId());
        return task;
    }

    @Override
    public void updateTaskStatus(Long taskId, TaskStatus status, String errorMsg) {
        indexTaskMapper.updateStatus(taskId, status.name(), errorMsg);
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
                .taskType(com.sigma.ai.evaluation.types.TaskType.valueOf(po.getTaskType()))
                .triggerCommit(po.getTriggerCommit())
                .status(TaskStatus.valueOf(po.getStatus()))
                .startedAt(po.getStartedAt() != null
                        ? po.getStartedAt().toInstant(ZoneOffset.UTC) : null)
                .finishedAt(po.getFinishedAt() != null
                        ? po.getFinishedAt().toInstant(ZoneOffset.UTC) : null)
                .errorMsg(po.getErrorMsg())
                .build();
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofEpochSecond(epochMillis / 1000, 0, ZoneOffset.UTC);
    }
}
