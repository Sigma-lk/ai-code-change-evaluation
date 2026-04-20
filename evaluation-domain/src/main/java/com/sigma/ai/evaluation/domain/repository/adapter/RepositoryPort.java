package com.sigma.ai.evaluation.domain.repository.adapter;

import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;

import java.util.List;
import java.util.Optional;

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

    /**
     * 按克隆 URL（HTTPS 或 SSH，与 GitHub payload 中 {@code clone_url}/{@code ssh_url} 一致即可）查找活跃仓库。
     * <p>在应用层对 URL 做规范化后与 {@link com.sigma.ai.evaluation.domain.repository.util.CloneUrlNormalizer} 比对。
     *
     * @param cloneUrlOrSsh 原始克隆地址
     * @return 匹配到的仓库；无匹配或非活跃时为空
     */
    Optional<RepositoryInfo> findActiveByCloneUrl(String cloneUrlOrSsh);
}
