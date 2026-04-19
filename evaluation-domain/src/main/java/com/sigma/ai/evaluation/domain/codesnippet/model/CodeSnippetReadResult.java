package com.sigma.ai.evaluation.domain.codesnippet.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 源码片段读取结果（领域层），由 {@link com.sigma.ai.evaluation.domain.codesnippet.CodeSnippetReadService} 产出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSnippetReadResult {

    private String repoId;
    private String resolvedAbsolutePath;
    private String relativePath;
    private String encoding;
    private Integer totalLinesInFile;
    private Integer returnedStartLine;
    private Integer returnedEndLine;
    private boolean truncated;
    private String truncationNote;
    private String content;
}
