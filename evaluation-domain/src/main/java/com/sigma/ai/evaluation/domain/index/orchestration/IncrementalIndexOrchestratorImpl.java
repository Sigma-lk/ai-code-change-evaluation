package com.sigma.ai.evaluation.domain.index.orchestration;

import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.codegraph.model.CommitNode;
import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.service.CodeGraphService;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.index.adapter.IndexTaskPort;
import com.sigma.ai.evaluation.domain.index.adapter.ParseErrorPort;
import com.sigma.ai.evaluation.domain.index.model.CommitEvent;
import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.domain.index.model.IncrementalIndexResult;
import com.sigma.ai.evaluation.domain.index.model.IncrementalIndexResult.IncrementalIndexResultBuilder;
import com.sigma.ai.evaluation.domain.index.service.JavaAstParserService;
import com.sigma.ai.evaluation.domain.repository.adapter.CommitRecordPort;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.DiffLineStats;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.FileChangeType;
import com.sigma.ai.evaluation.types.TaskStatus;
import com.sigma.ai.evaluation.types.TaskType;
import com.sigma.ai.evaluation.types.exception.IndexTaskException;
import com.sigma.ai.evaluation.types.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link IncrementalIndexOrchestrator} 的默认实现：与原 {@code IndexConsumer} 内联逻辑一致，并增加 fetch 与行级统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncrementalIndexOrchestratorImpl implements IncrementalIndexOrchestrator {

    private final CommitRecordPort commitRecordPort;
    private final IndexTaskPort indexTaskPort;
    private final ParseErrorPort parseErrorPort;
    private final RepositoryPort repositoryPort;
    private final GitAdapter gitAdapter;
    private final GraphAdapter graphAdapter;
    private final CodeGraphService codeGraphService;
    private final JavaAstParserService javaAstParserService;
    private final EmbeddingStoreAdapter embeddingStoreAdapter;

    @Override
    public IncrementalIndexResult run(CommitEvent event) {
        String repoId = event.getRepoId();
        String commitHash = event.getCommitHash();

        if (commitRecordPort.isProcessed(repoId, commitHash)) {
            log.info("提交已处理，跳过增量索引: repoId={}, commitHash={}", repoId, commitHash);
            return IncrementalIndexResult.builder()
                    .skippedAlreadyProcessed(true)
                    .repoId(repoId)
                    .commitHash(commitHash)
                    .diffOldCommit(event.getParentCommitHash())
                    .diffNewCommit(commitHash)
                    .build();
        }

        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null) {
            log.error("仓库未注册，中止增量索引: repoId={}", repoId);
            throw ResourceNotFoundException.repositoryNotFound(repoId);
        }

        IndexTask task = indexTaskPort.createTask(IndexTask.builder()
                .repoId(repoId)
                .taskType(TaskType.INCREMENTAL)
                .triggerCommit(commitHash)
                .status(TaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build());

        IncrementalIndexResultBuilder resultBuilder = IncrementalIndexResult.builder()
                .skippedAlreadyProcessed(false)
                .taskId(task.getId())
                .repoId(repoId)
                .commitHash(commitHash);

        try {
            gitAdapter.cloneOrPull(repo.getCloneUrl(), repo.getBranch(), repo.getLocalPath());
            gitAdapter.fetch(repo.getLocalPath());

            String parent = event.getParentCommitHash();
            String oldForDiff;
            String newForDiff = commitHash;
            List<ChangedFile> changedFiles;

            if (isGitHubNullSha(parent)) {
                oldForDiff = commitHash + '^';
                changedFiles = gitAdapter.diffCommitAgainstFirstParent(repo.getLocalPath(), commitHash);
                log.info("parent 为全 0 或空，使用第一父提交 diff: repoId={}, new={}", repoId, commitHash);
            } else {
                oldForDiff = parent;
                changedFiles = gitAdapter.diffCommits(repo.getLocalPath(), parent, commitHash);
            }

            resultBuilder.diffOldCommit(oldForDiff).diffNewCommit(newForDiff).changedJavaFiles(changedFiles);

            DiffLineStats lineStats = gitAdapter.diffLineStats(repo.getLocalPath(), oldForDiff, newForDiff);
            resultBuilder.lineStats(lineStats);

            log.info("增量索引变更 Java 文件数: {}, repoId={}, insertions={}, deletions={}",
                    changedFiles.size(), repoId, lineStats.getTotalInsertions(), lineStats.getTotalDeletions());

            if (changedFiles.isEmpty()) {
                indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
                return resultBuilder.successfulParseResults(List.of()).build();
            }

            List<Path> sourceRoots = findSourceRoots(Path.of(repo.getLocalPath()));
            List<ParseResult> parseResults = processChangedFiles(changedFiles, sourceRoots, repoId, task.getId());
            resultBuilder.successfulParseResults(parseResults);

            graphAdapter.batchMergeCommitNodes(List.of(CommitNode.builder()
                    .hash(commitHash)
                    .branch(event.getBranch())
                    .author(event.getPusher())
                    .email("")
                    .timestamp(System.currentTimeMillis())
                    .repoId(repoId)
                    .build()));

            commitRecordPort.markProcessed(repoId, commitHash, event.getPusher(),
                    System.currentTimeMillis(), changedFiles.size());

            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
            log.info("增量索引完成: repoId={}, commitHash={}", repoId, commitHash);
            return resultBuilder.build();

        } catch (Exception e) {
            log.error("增量索引失败: taskId={}, repoId={}", task.getId(), repoId, e);
            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.FAIL, e.getMessage());
            throw IndexTaskException.incrementalIndexFailed(e);
        }
    }

    private static boolean isGitHubNullSha(String parent) {
        if (parent == null || parent.isBlank()) {
            return true;
        }
        return parent.length() == 40 && parent.chars().allMatch(ch -> ch == '0');
    }

    private List<ParseResult> processChangedFiles(List<ChangedFile> changedFiles, List<Path> sourceRoots,
                                                  String repoId, Long taskId) {
        List<ParseResult> results = new ArrayList<>();

        for (ChangedFile file : changedFiles) {
            if (file.getChangeType() == FileChangeType.DELETED) {
                List<String> embeddingKeys = graphAdapter.listEmbeddingKeysForJavaFile(file.getAbsolutePath());
                embeddingStoreAdapter.deleteEmbeddingsByNodeIds(embeddingKeys);
                graphAdapter.deleteFileNodes(file.getAbsolutePath());
                continue;
            }

            if (file.getChangeType() == FileChangeType.MODIFIED) {
                graphAdapter.deleteFileOutgoingRelations(file.getAbsolutePath());
            }

            ParseResult result = javaAstParserService.parse(
                    Path.of(file.getAbsolutePath()), sourceRoots, repoId);

            if (!result.isSuccess()) {
                parseErrorPort.record(taskId, file.getAbsolutePath(),
                        "PARSE_ERROR", result.getErrorMessage());
                log.warn("增量解析失败: {}", file.getAbsolutePath());
                continue;
            }
            results.add(result);
        }

        if (!results.isEmpty()) {
            codeGraphService.batchWriteParseResults(results);
        }
        return results;
    }

    private List<Path> findSourceRoots(Path repoPath) {
        List<Path> roots = new ArrayList<>();
        try (var stream = Files.walk(repoPath)) {
            stream.filter(p -> p.endsWith(Path.of("src/main/java")))
                    .filter(Files::isDirectory)
                    .forEach(roots::add);
        } catch (IOException e) {
            log.warn("查找源根目录失败: {}", repoPath, e);
        }
        return roots;
    }
}
