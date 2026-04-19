package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import com.sigma.ai.evaluation.types.GraphRelationGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 子图多跳展开查询（领域层），由 {@link com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter#expandSubgraph} 消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphExpandQuery {

    private String repoId;

    /**
     * 已解析的方法种子 id（Method.id）。
     */
    @Builder.Default
    private List<String> seedMethodIds = List.of();

    @Builder.Default
    private Set<GraphRelationGroup> includeRelationGroups = new LinkedHashSet<>();

    @Builder.Default
    private int upstreamMaxHops = 3;

    @Builder.Default
    private int downstreamMaxHops = 3;

    @Builder.Default
    private int maxNodes = 500;

    @Builder.Default
    private int maxEdges = 2000;

    @Builder.Default
    private int maxPaths = 50;
}
