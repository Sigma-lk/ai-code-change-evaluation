package com.sigma.ai.evaluation.domain.aicontext;

import com.sigma.ai.evaluation.types.GraphRelationGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 领域层 AI 上下文组装入参（与 API 契约字段对齐，便于 HTTP 层做 JSON 映射）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContextAssemblyInput {

    private String repoId;
    private String commitHash;

    @Builder.Default
    private List<String> changedMethodIds = new ArrayList<>();

    @Builder.Default
    private List<String> changedTypeQualifiedNames = new ArrayList<>();

    @Builder.Default
    private List<String> changedFilePaths = new ArrayList<>();

    @Builder.Default
    private List<String> semanticQueries = new ArrayList<>();

    @Builder.Default
    private Boolean useSemanticHitsAsGraphSeeds = Boolean.TRUE;

    private AiContextInclude include;
    private AiGraphExpandConfig graph;

    @Builder.Default
    private Integer semanticTopK = 10;

    @Builder.Default
    private Float semanticMinScore = 0.2f;

    @Builder.Default
    private Set<GraphRelationGroup> includeRelationGroups = new LinkedHashSet<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiContextInclude {
        @Builder.Default
        private Boolean graph = Boolean.TRUE;
        @Builder.Default
        private Boolean semantic = Boolean.TRUE;
        @Builder.Default
        private Boolean summary = Boolean.TRUE;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiGraphExpandConfig {
        @Builder.Default
        private Integer maxHops = 3;
        private Integer upstreamMaxHops;
        private Integer downstreamMaxHops;
        @Builder.Default
        private Integer maxNodes = 500;
        @Builder.Default
        private Integer maxEdges = 2000;
        @Builder.Default
        private Integer maxPaths = 50;
    }
}
