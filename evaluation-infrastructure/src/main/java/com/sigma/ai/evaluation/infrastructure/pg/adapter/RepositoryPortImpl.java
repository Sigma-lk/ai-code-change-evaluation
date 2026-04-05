package com.sigma.ai.evaluation.infrastructure.pg.adapter;

import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.infrastructure.pg.mapper.RepositoryMapper;
import com.sigma.ai.evaluation.infrastructure.pg.po.RepositoryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link RepositoryPort} 的 PostgreSQL 实现。
 */
@Component
@RequiredArgsConstructor
public class RepositoryPortImpl implements RepositoryPort {

    private final RepositoryMapper repositoryMapper;

    @Override
    public List<RepositoryInfo> findAllActive() {
        return repositoryMapper.selectAllActive().stream()
                .map(this::toInfo)
                .toList();
    }

    @Override
    public RepositoryInfo findById(String repoId) {
        RepositoryPO po = repositoryMapper.selectById(repoId);
        return po == null ? null : toInfo(po);
    }

    private RepositoryInfo toInfo(RepositoryPO po) {
        return RepositoryInfo.builder()
                .id(po.getId())
                .name(po.getName())
                .cloneUrl(po.getCloneUrl())
                .branch(po.getBranch())
                .localPath(po.getLocalPath())
                .status(po.getStatus())
                .build();
    }
}
