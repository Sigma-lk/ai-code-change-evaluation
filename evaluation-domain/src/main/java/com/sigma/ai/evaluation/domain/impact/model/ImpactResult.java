package com.sigma.ai.evaluation.domain.impact.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 影响面分析结果，包含受变更波及的方法与子类型集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactResult {

    /**
     * 受影响的方法 ID 列表（在 maxHops 跳内调用了变更方法的上游方法）。
     */
    private List<String> impactedMethodIds;

    /**
     * 受影响的子类型全限定名列表（继承/实现了变更类型的子类型）。
     */
    private List<String> impactedSubTypeNames;

    /** 本次分析实际使用的最大跳数 */
    private int maxHops;
}
