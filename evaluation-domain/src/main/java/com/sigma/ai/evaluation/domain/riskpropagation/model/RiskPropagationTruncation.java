package com.sigma.ai.evaluation.domain.riskpropagation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 截断与告警元数据，供下游 LLM 识别覆盖范围上限。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPropagationTruncation {

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
