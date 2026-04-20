package com.sigma.ai.evaluation.domain.index.model;

import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.DiffLineStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次增量索引编排的产出，供 Webhook 日志、Dify 与下游 LLM 使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncrementalIndexResult {

    private boolean skippedAlreadyProcessed;

    private Long taskId;

    private String repoId;

    /** 实际用于幂等与 Commit 节点写入的提交 */
    private String commitHash;

    /** diff 左侧实际使用的提交（可能与事件 parent 不同） */
    private String diffOldCommit;

    /** diff 右侧提交，通常与 commitHash 一致 */
    private String diffNewCommit;

    @Builder.Default
    private List<ChangedFile> changedJavaFiles = new ArrayList<>();

    @Builder.Default
    private DiffLineStats lineStats = DiffLineStats.builder().build();

    /**
     * 本次成功解析并写入图谱的 Java 文件结果（用于收集 AST 节点，不做图搜索扩展）。
     */
    @Builder.Default
    private List<ParseResult> successfulParseResults = new ArrayList<>();
}
