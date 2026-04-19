package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Neo4j Commit / CHANGED_IN 解析出的种子快照（可能为空）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitSeedSnapshot {

    @Builder.Default
    private List<String> methodIds = new ArrayList<>();

    @Builder.Default
    private List<String> typeQualifiedNames = new ArrayList<>();

    /**
     * 是否在图中找到任何 CHANGED_IN 关联（用于决定是否走 Git diff 兜底）。
     */
    private boolean graphHit;
}
