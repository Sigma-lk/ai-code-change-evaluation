package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * AI 分析上下文组合接口请求体。
 *
 * <p>与下游提示词模板约定字段名稳定；扩展时优先只增可选字段并递增 {@link AiAnalysisContextResponse#getSchemaVersion()}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAnalysisContextRequest {

    /** 必填：仓库 ID */
    private String repoId;

    /** 可选：用于服务端解析变更种子（Neo4j CHANGED_IN 优先，不足则 Git diff 兜底） */
    private String commitHash;

    @Builder.Default
    private List<String> changedMethodIds = new ArrayList<>();

    @Builder.Default
    private List<String> changedTypeQualifiedNames = new ArrayList<>();

    @Builder.Default
    private List<String> changedFilePaths = new ArrayList<>();

    @Builder.Default
    private List<String> semanticQueries = new ArrayList<>();

    /**
     * 为 true 时将语义 TopK 并入图展开种子；默认 true。
     */
    @Builder.Default
    private Boolean useSemanticHitsAsGraphSeeds = Boolean.TRUE;

    /**
     * 是否生成各块；未指定时由服务端视为全开。
     */
    private AiContextInclude include;

    private AiGraphExpandConfig graph;

    @Builder.Default
    private Integer semanticTopK = 10;

    @Builder.Default
    private Float semanticMinScore = 0.2f;

    /**
     * 图展开分组；未指定时使用服务端默认（不含 {@link GraphRelationGroup#IMPORTS} 与 {@link GraphRelationGroup#STRUCTURE}）。
     */
    @Builder.Default
    private Set<GraphRelationGroup> includeRelationGroups = new LinkedHashSet<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
