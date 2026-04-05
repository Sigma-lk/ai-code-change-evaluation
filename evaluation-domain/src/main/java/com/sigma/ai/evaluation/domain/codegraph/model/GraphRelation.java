package com.sigma.ai.evaluation.domain.codegraph.model;

import com.sigma.ai.evaluation.types.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 表示知识图谱中一条有向关系（边）。
 *
 * <p>fromNodeId / toNodeId 均为对应节点的业务唯一键值，
 * fromKeyName / toKeyName 指定该唯一键在节点属性中的名称
 * （如 Type 节点用 qualifiedName，Method/Field 节点用 id）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRelation {

    /** 起点节点业务键值 */
    private String fromNodeId;

    /** 起点节点 Neo4j 标签（如 "Type"、"Method"） */
    private String fromNodeLabel;

    /** 起点节点业务键属性名（如 "qualifiedName"、"id"、"path"、"hash"） */
    private String fromKeyName;

    /** 关系类型 */
    private RelationType type;

    /** 终点节点业务键值 */
    private String toNodeId;

    /** 终点节点 Neo4j 标签 */
    private String toNodeLabel;

    /** 终点节点业务键属性名 */
    private String toKeyName;

    /**
     * 关系上的可选属性，如 CALLS 关系的 lineNo。
     * 为 null 时不写入额外属性。
     */
    private Map<String, Object> properties;
}
