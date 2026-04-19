package com.sigma.ai.evaluation.domain.aicontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领域层 AI 上下文组装出参（与 API 响应字段对齐）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiContextAssemblyOutput {

    @Builder.Default
    private String schemaVersion = "1.0";

    private Meta meta;
    private Seeds seeds;
    private GraphBlock graph;
    private SemanticBlock semantic;
    private Summary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String repoId;
        private String commitHash;
        private String generatedAt;
        private Truncation truncation;
        private Long elapsedMs;
        private String evidenceScope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Truncation {
        @Builder.Default
        private Boolean truncated = Boolean.FALSE;
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Seeds {
        @Builder.Default
        private List<SeedMethod> methods = new ArrayList<>();
        @Builder.Default
        private List<SeedType> types = new ArrayList<>();
        @Builder.Default
        private List<SeedFile> files = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeedMethod {
        private String methodId;
        private String signature;
        private String ownerType;
        private String filePath;
        private Integer lineStart;
        private Integer lineEnd;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeedType {
        private String qualifiedName;
        private String filePath;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeedFile {
        private String path;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphBlock {
        @Builder.Default
        private List<GraphNode> nodes = new ArrayList<>();
        @Builder.Default
        private List<GraphEdge> edges = new ArrayList<>();
        @Builder.Default
        private List<GraphPath> paths = new ArrayList<>();
        @Builder.Default
        private List<GraphEdge> inheritance = new ArrayList<>();
        @Builder.Default
        private List<FieldCoupling> fieldCoupling = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        private String id;
        private String label;
        @Builder.Default
        private List<String> seedIds = new ArrayList<>();
        private String primarySeedId;
        @Builder.Default
        private Map<String, Object> properties = new LinkedHashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {
        private String from;
        private String to;
        private String type;
        private Integer hopFromSeed;
        private String directionToSeed;
        private String referenceSeedId;
        @Builder.Default
        private List<String> seedIds = new ArrayList<>();
        @Builder.Default
        private Map<String, Object> properties = new LinkedHashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphPath {
        @Builder.Default
        private List<String> nodeIds = new ArrayList<>();
        @Builder.Default
        private List<String> relTypes = new ArrayList<>();
        private Integer length;
        private String referenceSeedId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldCoupling {
        private String fieldId;
        private String ownerTypeQualifiedName;
        private String fieldSimpleName;
        @Builder.Default
        private List<String> readers = new ArrayList<>();
        @Builder.Default
        private List<String> writers = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticBlock {
        @Builder.Default
        private List<SemanticQueryHits> queries = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticQueryHits {
        private String query;
        @Builder.Default
        private List<SemanticHit> hits = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticHit {
        private String nodeId;
        private String nodeType;
        private String qualifiedName;
        private Float score;
        private String signature;
        private String filePath;
        private Integer lineStart;
        private Integer lineEnd;
        private Boolean inGraph;
        private String note;
        private String evidenceSnippet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        @Builder.Default
        private Map<String, Integer> counts = new LinkedHashMap<>();
        @Builder.Default
        private Map<String, Integer> relationTypeHistogram = new LinkedHashMap<>();
    }
}
