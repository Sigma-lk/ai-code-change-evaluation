package com.sigma.ai.evaluation.domain.impact.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 影响面分析请求，封装本次变更的方法/类型及遍历深度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactQuery {

    /**
     * 本次变更的方法 ID 列表，格式：{ownerQualifiedName}#{methodName}({paramTypes})。
     * 为 null 或空集合时跳过方法维度分析。
     */
    private List<String> changedMethodIds;

    /**
     * 本次变更的类型全限定名列表。
     * 为 null 或空集合时跳过子类型维度分析。
     */
    private List<String> changedTypeNames;

    /**
     * 图遍历最大跳数，控制分析范围深度（建议 1-5）。
     */
    private int maxHops;
}
