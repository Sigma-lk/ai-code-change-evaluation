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
 * 展开子图中的节点（含多种子归因）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpandedNode {

    private String id;

    private String label;

    /**
     * 到达该节点的种子 Method.id 列表（去重且稳定顺序）。
     */
    @Builder.Default
    private List<String> seedIds = new ArrayList<>();

    /**
     * 作为 {@link #seedIds} 的 tie-break：取 hop 最小的种子 id；多种子等距时写入 warnings。
     */
    private String primarySeedId;

    /**
     * 各种子到该节点的最小跳数（仅计 CALLS 链，用于归因）。
     */
    @Builder.Default
    private Map<String, Integer> minHopsBySeed = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> properties = new LinkedHashMap<>();
}
