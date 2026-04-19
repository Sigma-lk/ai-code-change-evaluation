package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 子图展开结果（领域层中间结构）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpandedGraph {

    @Builder.Default
    private List<ExpandedNode> nodes = new ArrayList<>();

    @Builder.Default
    private List<ExpandedEdge> edges = new ArrayList<>();

    @Builder.Default
    private List<ExpandedPath> paths = new ArrayList<>();

    /** 类型层次边（EXTENDS / IMPLEMENTS），便于与 CALLS 分开展示 */
    @Builder.Default
    private List<ExpandedEdge> inheritanceEdges = new ArrayList<>();

    @Builder.Default
    private List<FieldCouplingEntry> fieldCoupling = new ArrayList<>();

    private boolean truncated;

    @Builder.Default
    private List<String> truncationWarnings = new ArrayList<>();
}
