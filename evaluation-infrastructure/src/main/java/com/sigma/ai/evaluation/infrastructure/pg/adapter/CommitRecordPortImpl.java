package com.sigma.ai.evaluation.infrastructure.pg.adapter;

import com.sigma.ai.evaluation.domain.repository.adapter.CommitRecordPort;
import com.sigma.ai.evaluation.infrastructure.pg.mapper.CommitRecordMapper;
import com.sigma.ai.evaluation.infrastructure.pg.po.CommitRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@link CommitRecordPort} 的 PostgreSQL 实现。
 */
@Component
@RequiredArgsConstructor
public class CommitRecordPortImpl implements CommitRecordPort {

    private final CommitRecordMapper commitRecordMapper;

    @Override
    public boolean isProcessed(String repoId, String commitHash) {
        return commitRecordMapper.countByRepoIdAndCommitHash(repoId, commitHash) > 0;
    }

    @Override
    public void markProcessed(String repoId, String commitHash, String author,
                               long commitTimeMillis, int changedFileCount) {
        CommitRecordPO po = new CommitRecordPO();
        po.setRepoId(repoId);
        po.setCommitHash(commitHash);
        po.setAuthor(author);
        po.setCommitTime(LocalDateTime.ofEpochSecond(
                commitTimeMillis / 1000, 0, ZoneOffset.UTC));
        po.setChangedFileCount(changedFileCount);
        // 1:PROCESSED
        po.setStatus(1);
        commitRecordMapper.insert(po);
    }
}
