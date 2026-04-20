package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskPropagationResponse {

    private String repoId;

    private int effectiveDepth;

    @Builder.Default
    private List<RiskSeedPropagationResultDto> results = new ArrayList<>();

    private RiskPropagationTruncationDto truncation;
}
