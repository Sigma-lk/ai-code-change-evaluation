package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 从 Neo4j 批量解析出的代码节点展示字段，供 seeds、graph.nodes、semantic 命中共用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeNodeDetail {

    /** 与 Milvus node_id / 图业务键一致 */
    private String id;

    /** Neo4j 标签简写：Method、Type、Field、JavaFile 等 */
    private String label;

    private String qualifiedName;

    private String signature;

    private String ownerQualifiedName;

    private String filePath;

    private Integer lineStart;

    private Integer lineEnd;

    private String simpleName;

    /**
     * 扩展属性（如 accessModifier），避免 DTO 爆炸；组装 API 时可挑选写入。
     */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}
