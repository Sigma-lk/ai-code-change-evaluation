package com.sigma.ai.evaluation.domain.riskpropagation.impl;

import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.riskpropagation.RiskPropagationDepthPolicy;
import com.sigma.ai.evaluation.domain.riskpropagation.RiskPropagationService;
import com.sigma.ai.evaluation.domain.riskpropagation.model.RiskPropagationQuery;
import com.sigma.ai.evaluation.domain.riskpropagation.model.RiskPropagationResult;
import com.sigma.ai.evaluation.types.exception.ParamValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 委托 {@link GraphAdapter#propagateRisks} 执行 Neo4j 有界遍历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskPropagationServiceImpl implements RiskPropagationService {

    private final GraphAdapter graphAdapter;

    @Override
    public RiskPropagationResult propagate(RiskPropagationQuery query) {
        if (query == null || query.getRepoId() == null || query.getRepoId().isBlank()) {
            throw ParamValidationException.repoIdEmpty();
        }
        int effectiveDepth = RiskPropagationDepthPolicy.resolveEffectiveDepth(query.getPropagationMaxDepth());
        log.info("风险传播查询: repoId={}, seedCount={}, effectiveDepth={}, maxNodes={}, maxEdges={}",
                query.getRepoId(),
                query.getRiskSeeds() == null ? 0 : query.getRiskSeeds().size(),
                effectiveDepth,
                query.getMaxNodes(),
                query.getMaxEdges());

        RiskPropagationQuery normalized = RiskPropagationQuery.builder()
                .repoId(query.getRepoId())
                .riskSeeds(query.getRiskSeeds() == null ? List.of() : query.getRiskSeeds())
                .propagationMaxDepth(effectiveDepth)
                .maxNodes(query.getMaxNodes())
                .maxEdges(query.getMaxEdges())
                .build();

        RiskPropagationResult result = graphAdapter.propagateRisks(normalized);
        log.info("风险传播完成: repoId={}, resultRows={}", query.getRepoId(),
                result.getResults() == null ? 0 : result.getResults().size());
        return result;
    }
}
