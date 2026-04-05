package com.sigma.ai.evaluation.domain.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仓库注册信息领域对象，对应 t_repository 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryInfo {

    private String id;

    /** 仓库名称 */
    private String name;

    /** 克隆 URL */
    private String cloneUrl;

    /** 目标分支 */
    private String branch;

    /** 本地克隆路径 */
    private String localPath;

    /** 仓库状态（ACTIVE / INACTIVE） */
    private String status;
}
