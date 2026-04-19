package com.sigma.ai.evaluation.domain.codesearch.model;

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
public class TextSearchResult {

    private List<TextSearchHit> hits;
    private int scannedFiles;
    private boolean truncatedByMaxHits;
    private boolean truncatedByMaxFiles;

    public static TextSearchResult empty() {
        return TextSearchResult.builder()
                .hits(new ArrayList<>())
                .scannedFiles(0)
                .truncatedByMaxHits(false)
                .truncatedByMaxFiles(false)
                .build();
    }
}
