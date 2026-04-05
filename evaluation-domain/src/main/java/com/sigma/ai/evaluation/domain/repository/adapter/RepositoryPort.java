package com.sigma.ai.evaluation.domain.repository.adapter;

import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;

import java.util.List;

/**
 * 仓库注册信息持久化 Port，操作 t_repository 表。
 * 由 evaluation-infrastructure 模块实现。
 */
public interface RepositoryPort {

    /**
     * 查询所有活跃状态（ACTIVE）的仓库列表，供全量索引调度使用。
     *
     * @return 仓库信息列表
     */
    List<RepositoryInfo> findAllActive();

    /**
     * 根据 ID 查询仓库信息。
     *
     * @param repoId 仓库 ID
     * @return 仓库信息，不存在时返回 null
     */
    RepositoryInfo findById(String repoId);
}
