package com.sigma.ai.evaluation.domain.impact.service;

import com.sigma.ai.evaluation.domain.impact.model.ImpactQuery;
import com.sigma.ai.evaluation.domain.impact.model.ImpactResult;

/**
 * 影响面分析服务，基于 Neo4j 图遍历计算代码变更的影响范围。
 */
public interface ImpactAnalysisService {

    /**
     * 根据变更查询条件，分析受影响的方法调用链与子类型集合。
     *
     * @param query 变更查询条件，包含变更方法/类型列表与最大遍历跳数
     * @return 影响面分析结果
     */
    ImpactResult analyze(ImpactQuery query);
}
