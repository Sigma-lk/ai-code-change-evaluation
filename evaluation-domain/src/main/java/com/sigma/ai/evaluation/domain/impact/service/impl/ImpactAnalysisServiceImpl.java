package com.sigma.ai.evaluation.domain.impact.service.impl;

import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.impact.model.ImpactQuery;
import com.sigma.ai.evaluation.domain.impact.model.ImpactResult;
import com.sigma.ai.evaluation.domain.impact.service.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 影响面分析服务实现，委托 GraphAdapter 执行 Neo4j 图遍历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactAnalysisServiceImpl implements ImpactAnalysisService {

    private final GraphAdapter graphAdapter;

    @Override
    public ImpactResult analyze(ImpactQuery query) {
        log.info("开始影响面分析，变更方法数={}, 变更类型数={}, maxHops={}",
                size(query.getChangedMethodIds()), size(query.getChangedTypeNames()), query.getMaxHops());

        List<String> impactedMethods = Collections.emptyList();
        if (query.getChangedMethodIds() != null && !query.getChangedMethodIds().isEmpty()) {
            impactedMethods = graphAdapter.findCallerMethodIds(query.getChangedMethodIds(), query.getMaxHops());
        }

        List<String> impactedSubTypes = Collections.emptyList();
        if (query.getChangedTypeNames() != null && !query.getChangedTypeNames().isEmpty()) {
            impactedSubTypes = graphAdapter.findSubTypeQualifiedNames(query.getChangedTypeNames(), query.getMaxHops());
        }

        log.info("影响面分析完成，受影响方法数={}, 受影响子类型数={}",
                impactedMethods.size(), impactedSubTypes.size());

        return ImpactResult.builder()
                .impactedMethodIds(impactedMethods)
                .impactedSubTypeNames(impactedSubTypes)
                .maxHops(query.getMaxHops())
                .build();
    }

    private int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
