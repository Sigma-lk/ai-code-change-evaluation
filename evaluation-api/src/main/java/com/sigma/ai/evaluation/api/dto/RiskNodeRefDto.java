package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险传播种子或链路上的节点引用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskNodeRefDto {

    /** {@code TYPE}、{@code METHOD}、{@code FIELD} */
    private String kind;

    private String qualifiedName;

    private String filePath;
}
