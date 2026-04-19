package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本搜索响应：命中列表与扫描统计，便于下游 AI 再调用 {@code /code/snippet} 拉上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeTextSearchResponse {

    private String repoId;

    private String query;

    private boolean useRegex;

    private boolean caseInsensitive;

    private String glob;

    @Builder.Default
    private List<CodeTextSearchHit> hits = new ArrayList<>();

    /** 实际扫描过的普通文件数量 */
    private int scannedFiles;

    /** 因超过 maxHits 停止收集 */
    @Builder.Default
    private boolean truncatedByMaxHits = false;

    /** 因超过 maxFilesScanned 停止遍历 */
    @Builder.Default
    private boolean truncatedByMaxFiles = false;

    private Long elapsedMs;
}
