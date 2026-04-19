package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段读写耦合聚合（FIELD_ACCESS 分组开启时）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldCouplingEntry {

    /** Field 节点 id */
    private String fieldId;

    private String ownerTypeQualifiedName;

    private String fieldSimpleName;

    @Builder.Default
    private List<String> readerMethodIds = new ArrayList<>();

    @Builder.Default
    private List<String> writerMethodIds = new ArrayList<>();
}
