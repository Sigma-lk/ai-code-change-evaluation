package com.sigma.ai.evaluation.domain.codesearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextSearchHit {

    private String absolutePath;
    private String relativePath;
    private int lineNumber;
    private String lineText;
}
