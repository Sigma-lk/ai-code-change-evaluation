package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 展开子图中的边。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpandedEdge {

    private String fromId;

    private String toId;

    /** {@link com.sigma.ai.evaluation.types.RelationType} 名称 */
    private String relationType;

    /**
     * 相对 {@link #referenceSeedId} 的跳数（在 CALLS 语义下）。
     */
    private int hopFromSeed;

    /** upstream / downstream / lateral */
    private String directionToSeed;

    /**
     * 本边归因参考种子（取端点 hop 较小一侧的 reference 或合并策略）。
     */
    private String referenceSeedId;

    @Builder.Default
    private List<String> seedIds = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> properties = new LinkedHashMap<>();
}
