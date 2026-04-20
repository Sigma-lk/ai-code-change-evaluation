package com.sigma.ai.evaluation.domain.riskpropagation;

import com.sigma.ai.evaluation.domain.riskpropagation.model.RiskPropagationQuery;
import com.sigma.ai.evaluation.domain.riskpropagation.model.RiskPropagationResult;

/**
 * 基于代码知识图谱的风险传播查询（领域服务）。
 */
public interface RiskPropagationService {

    /**
     * 按仓库与种子节点列表展开风险传播链。
     *
     * @param query 查询参数（repoId、种子列表等）
     * @return 与种子顺序对齐的传播结果
     */
    RiskPropagationResult propagate(RiskPropagationQuery query);
}
