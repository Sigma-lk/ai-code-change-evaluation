package com.sigma.ai.evaluation.domain.changeevidence;

import com.sigma.ai.evaluation.domain.repository.model.DiffLineStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向 LLM / Dify 的变更证据文档：节点仅保留 kind、qualifiedName、filePath 及与行号相关的 unified diff 片段，不包含图谱多跳搜索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEvidenceDocument {

    private Meta meta;

    @Builder.Default
    private List<Map<String, Object>> changedFiles = new ArrayList<>();

    private DiffLineStats lineStats;

    /**
     * 从本次解析结果汇总的节点（Type / Method / Field）及可选代码片段。
     */
    @Builder.Default
    private List<NodeEvidence> nodes = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> truncation = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String repoId;
        private String commitHash;
        private String before;
        private String after;
        private String ref;
        private String branch;
        private List<String> commitMessages;
        /** 与 t_repository 对齐时使用的 clone_url 原始值（脱敏由调用方保证） */
        private String matchedCloneUrl;
        /** 本次推送 tip 提交（与 {@code after} 一致）的作者显示名，如来自 GitHub webhook 的 {@code head_commit.author.name} */
        private String authorName;
        /** 本次推送 tip 提交的作者邮箱，如来自 GitHub webhook 的 {@code head_commit.author.email} */
        private String authorEmail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeEvidence {
        /** TYPE、METHOD、FIELD */
        private String kind;
        /** 类型全限定名；方法/字段为与图谱一致的 id（如 {@code owner#name} 或方法签名 id） */
        private String qualifiedName;
        /** 本地绝对路径 */
        private String filePath;
        /** 与该节点在新文件中行范围相关的 unified diff 片段 */
        private String diffSnippet;
    }
}
