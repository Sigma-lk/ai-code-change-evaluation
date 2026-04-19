package com.sigma.ai.evaluation.domain.codegraph.model.expand;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表性路径（供 LLM 叙述调用链）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpandedPath {

    @Builder.Default
    private List<String> nodeIds = new ArrayList<>();

    @Builder.Default
    private List<String> relTypes = new ArrayList<>();

    private int length;

    private String referenceSeedId;
}
