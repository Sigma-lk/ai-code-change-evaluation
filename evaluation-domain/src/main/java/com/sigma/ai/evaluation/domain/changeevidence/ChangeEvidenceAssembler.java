package com.sigma.ai.evaluation.domain.changeevidence;

import com.sigma.ai.evaluation.domain.changeevidence.ChangeEvidenceDocument.Meta;
import com.sigma.ai.evaluation.domain.changeevidence.ChangeEvidenceDocument.NodeEvidence;
import com.sigma.ai.evaluation.domain.codegraph.model.FieldNode;
import com.sigma.ai.evaluation.domain.codegraph.model.MethodNode;
import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.model.TypeNode;
import com.sigma.ai.evaluation.domain.index.model.IncrementalIndexResult;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将增量索引结果组装为变更证据文档：节点仅含 kind、qualifiedName、filePath 及与行号相关的 unified diff 片段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeEvidenceAssembler {

    private static final int DEFAULT_SNIPPET_PADDING = 6;
    private static final int DEFAULT_MAX_SNIPPET_CHARS = 12_000;
    private static final int DEFAULT_MAX_NODES_WITH_DIFF = 120;

    private final RepositoryPort repositoryPort;
    private final GitAdapter gitAdapter;

    /**
     * 组装变更证据文档。
     *
     * @param repoId              仓库 ID
     * @param indexResult         增量索引编排结果（须含 diffOldCommit / diffNewCommit）
     * @param before              Webhook before
     * @param after               Webhook after
     * @param ref                 refs/heads/...
     * @param branch              解析后的分支名
     * @param commitMessages      commit message 列表
     * @param matchedCloneUrl     用于对齐的 clone_url
     * @param authorName          tip 提交作者名（可为 {@code null}）
     * @param authorEmail         tip 提交作者邮箱（可为 {@code null}）
     * @param maxNodesWithDiff    仅为前若干个节点计算 diffSnippet，超出则 diffSnippet 为空串
     * @param snippetPaddingLines 与新文件行号合并扩展的行数
     * @param maxSnippetChars       每个节点 diffSnippet 最大字符
     */
    public ChangeEvidenceDocument assemble(String repoId,
                                           IncrementalIndexResult indexResult,
                                           String before,
                                           String after,
                                           String ref,
                                           String branch,
                                           List<String> commitMessages,
                                           String matchedCloneUrl,
                                           String authorName,
                                           String authorEmail,
                                           int maxNodesWithDiff,
                                           int snippetPaddingLines,
                                           int maxSnippetChars) {
        List<String> messages = commitMessages == null ? List.of() : new ArrayList<>(commitMessages);
        Meta meta = Meta.builder()
                .repoId(repoId)
                .commitHash(indexResult.getCommitHash())
                .before(before)
                .after(after)
                .ref(ref)
                .branch(branch)
                .commitMessages(messages)
                .matchedCloneUrl(matchedCloneUrl)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .build();

        List<Map<String, Object>> fileRows = new ArrayList<>();
        for (ChangedFile f : indexResult.getChangedJavaFiles()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", f.getRelativePath());
            row.put("changeType", f.getChangeType() != null ? f.getChangeType().name() : null);
            row.put("absolutePath", f.getAbsolutePath());
            fileRows.add(row);
        }

        RepositoryInfo repo = repositoryPort.findById(repoId);
        String localPath = repo != null ? repo.getLocalPath() : null;
        String oldC = indexResult.getDiffOldCommit();
        String newC = indexResult.getDiffNewCommit();
        boolean canDiff = localPath != null && !localPath.isBlank()
                && oldC != null && !oldC.isBlank()
                && newC != null && !newC.isBlank();

        Map<String, String> fullDiffByRelative = new HashMap<>();
        List<NodeEvidence> nodes = new ArrayList<>();
        int diffBudget = Math.max(0, maxNodesWithDiff);
        int totalNodes = countNodes(indexResult.getSuccessfulParseResults());
        List<String> warnings = new ArrayList<>();
        if (totalNodes > maxNodesWithDiff) {
            warnings.add("节点总数 " + totalNodes + " 超过 diffSnippet 计算上限 " + maxNodesWithDiff + "，后续节点 diffSnippet 为空");
        }

        for (ParseResult pr : indexResult.getSuccessfulParseResults()) {
            for (TypeNode t : pr.getTypes()) {
                boolean want = diffBudget > 0;
                nodes.add(buildNode(repoId, localPath, canDiff, oldC, newC, fullDiffByRelative,
                        "TYPE", t.getQualifiedName(), t.getFilePath(), t.getLineStart(), t.getLineEnd(),
                        want, snippetPaddingLines, maxSnippetChars));
                if (want) {
                    diffBudget--;
                }
            }
            for (MethodNode m : pr.getMethods()) {
                boolean want = diffBudget > 0;
                nodes.add(buildNode(repoId, localPath, canDiff, oldC, newC, fullDiffByRelative,
                        "METHOD", m.getId(), m.getFilePath(), m.getLineStart(), m.getLineEnd(),
                        want, snippetPaddingLines, maxSnippetChars));
                if (want) {
                    diffBudget--;
                }
            }
            for (FieldNode fld : pr.getFields()) {
                boolean want = diffBudget > 0;
                nodes.add(buildNode(repoId, localPath, canDiff, oldC, newC, fullDiffByRelative,
                        "FIELD", fld.getId(), fld.getFilePath(), fld.getLineNo(), fld.getLineNo(),
                        want, snippetPaddingLines, maxSnippetChars));
                if (want) {
                    diffBudget--;
                }
            }
        }

        Map<String, Object> trunc = new LinkedHashMap<>();
        trunc.put("warnings", warnings);

        return ChangeEvidenceDocument.builder()
                .meta(meta)
                .changedFiles(fileRows)
                .lineStats(indexResult.getLineStats())
                .nodes(nodes)
                .truncation(trunc)
                .build();
    }

    /**
     * 使用默认策略组装；{@code meta.authorName} / {@code meta.authorEmail} 将为 {@code null}。
     */
    public ChangeEvidenceDocument assemble(String repoId,
                                           IncrementalIndexResult indexResult,
                                           String before,
                                           String after,
                                           String ref,
                                           String branch,
                                           List<String> commitMessages,
                                           String matchedCloneUrl) {
        return assemble(repoId, indexResult, before, after, ref, branch, commitMessages, matchedCloneUrl,
                null, null,
                DEFAULT_MAX_NODES_WITH_DIFF, DEFAULT_SNIPPET_PADDING, DEFAULT_MAX_SNIPPET_CHARS);
    }

    private NodeEvidence buildNode(String repoId,
                                   String localPath,
                                   boolean canDiff,
                                   String oldCommit,
                                   String newCommit,
                                   Map<String, String> fullDiffByRelative,
                                   String kind,
                                   String qualifiedName,
                                   String absoluteFilePath,
                                   int lineStart,
                                   int lineEnd,
                                   boolean wantDiff,
                                   int paddingLines,
                                   int maxSnippetChars) {
        String diffSnippet = "";
        if (wantDiff && canDiff && absoluteFilePath != null && !absoluteFilePath.isBlank()) {
            String rel = toRelativePath(localPath, absoluteFilePath);
            if (rel != null && !rel.isBlank()) {
                try {
                    String full = fullDiffByRelative.computeIfAbsent(rel,
                            r -> gitAdapter.unifiedDiffForJavaFile(localPath, oldCommit, newCommit, r));
                    diffSnippet = UnifiedDiffRelevanceExtractor.extractForNewLineRange(
                            full, lineStart, lineEnd, paddingLines, maxSnippetChars);
                } catch (Exception e) {
                    log.warn("生成节点 diff 失败: repoId={}, file={}", repoId, absoluteFilePath, e);
                }
            }
        }
        return NodeEvidence.builder()
                .kind(kind)
                .qualifiedName(qualifiedName)
                .filePath(absoluteFilePath)
                .diffSnippet(diffSnippet != null ? diffSnippet : "")
                .build();
    }

    private static String toRelativePath(String localPath, String absoluteFilePath) {
        try {
            Path base = Path.of(localPath).toAbsolutePath().normalize();
            Path file = Path.of(absoluteFilePath).toAbsolutePath().normalize();
            return base.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            String normBase = localPath.endsWith("/") || localPath.endsWith("\\") ? localPath : localPath + java.io.File.separator;
            if (absoluteFilePath.startsWith(normBase)) {
                return absoluteFilePath.substring(normBase.length()).replace('\\', '/');
            }
            return absoluteFilePath.replace('\\', '/');
        }
    }

    private static int countNodes(List<ParseResult> results) {
        int n = 0;
        for (ParseResult pr : results) {
            n += pr.getTypes().size() + pr.getMethods().size() + pr.getFields().size();
        }
        return n;
    }
}
