package com.sigma.ai.evaluation.trigger.consumer;

import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.codegraph.model.CommitNode;
import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.service.CodeGraphService;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.index.adapter.IndexTaskPort;
import com.sigma.ai.evaluation.domain.index.adapter.ParseErrorPort;
import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.domain.index.service.JavaAstParserService;
import com.sigma.ai.evaluation.domain.repository.adapter.CommitRecordPort;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.types.FileChangeType;
import com.sigma.ai.evaluation.types.TaskStatus;
import com.sigma.ai.evaluation.types.TaskType;
import com.sigma.ai.evaluation.types.exception.IndexTaskException;
import com.sigma.ai.evaluation.types.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 增量索引消费者，处理 Git Hook 推送的提交事件。
 *
 * <p>Topic：{@code code-change-event}，Consumer Group：{@code index-service-group}。
 * 消费失败自动重试（Spring Kafka 默认 3 次），超限后投递死信 Topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexConsumer {

    private final CommitRecordPort commitRecordPort;
    private final IndexTaskPort indexTaskPort;
    private final ParseErrorPort parseErrorPort;
    private final RepositoryPort repositoryPort;
    private final GitAdapter gitAdapter;
    private final GraphAdapter graphAdapter;
    private final CodeGraphService codeGraphService;
    private final JavaAstParserService javaAstParserService;
    private final EmbeddingStoreAdapter embeddingStoreAdapter;

    /**
     * 消费 code-change-event 消息，对变更文件执行增量图谱更新。
     *
     * @param event          提交事件
     * @param acknowledgment 手动 ACK（enableAutoCommit=false 时使用）
     */
    @KafkaListener(
            topics = "${kafka.topic.code-change-event:code-change-event}",
            groupId = "${kafka.consumer.group-id:index-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCommitEvent(@Payload CommitEvent event, Acknowledgment acknowledgment) {
        log.info("收到提交事件: repoId={}, commitHash={}, pusher={}",
                event.getRepoId(), event.getCommitHash(), event.getPusher());

        try {
            processEvent(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("增量索引处理失败，将触发重试: repoId={}, commitHash={}",
                    event.getRepoId(), event.getCommitHash(), e);
            // 不 ACK，Spring Kafka 会重试
            throw e;
        }
    }

    private void processEvent(CommitEvent event) {
        String repoId = event.getRepoId();
        String commitHash = event.getCommitHash();

        // 1. 幂等检查
        if (commitRecordPort.isProcessed(repoId, commitHash)) {
            log.info("提交已处理，跳过: repoId={}, commitHash={}", repoId, commitHash);
            return;
        }

        // 2. 查询仓库信息
        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null) {
            log.error("仓库未注册，中止增量索引: repoId={}", repoId);
            throw ResourceNotFoundException.repositoryNotFound(repoId);
        }

        // 3. 创建增量任务
        IndexTask task = indexTaskPort.createTask(IndexTask.builder()
                .repoId(repoId)
                .taskType(TaskType.INCREMENTAL)
                .triggerCommit(commitHash)
                .status(TaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build());

        try {
            // 4. 获取变更文件列表
            List<ChangedFile> changedFiles = gitAdapter.diffCommits(
                    repo.getLocalPath(), event.getParentCommitHash(), commitHash);
            log.info("增量索引变更文件数: {}, repoId={}", changedFiles.size(), repoId);

            if (changedFiles.isEmpty()) {
                indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
                return;
            }

            // 5. 查找所有源根（用于 SymbolSolver）
            List<Path> sourceRoots = findSourceRoots(Path.of(repo.getLocalPath()));

            // 6. 处理变更文件
            processChangedFiles(changedFiles, sourceRoots, repoId, task.getId());

            // 7. 写入 Commit 节点
            graphAdapter.batchMergeCommitNodes(List.of(CommitNode.builder()
                    .hash(commitHash)
                    .branch(event.getBranch())
                    .author(event.getPusher())
                    .email("")
                    .timestamp(System.currentTimeMillis())
                    .repoId(repoId)
                    .build()));

            // 8. 标记已处理
            commitRecordPort.markProcessed(repoId, commitHash, event.getPusher(),
                    System.currentTimeMillis(), changedFiles.size());

            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
            log.info("增量索引完成: repoId={}, commitHash={}", repoId, commitHash);

        } catch (Exception e) {
            log.error("增量索引失败: taskId={}, repoId={}", task.getId(), repoId, e);
            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.FAIL, e.getMessage());
            throw IndexTaskException.incrementalIndexFailed(e);
        }
    }

    private void processChangedFiles(List<ChangedFile> changedFiles, List<Path> sourceRoots,
                                      String repoId, Long taskId) {
        List<ParseResult> results = new ArrayList<>();

        for (ChangedFile file : changedFiles) {
            if (file.getChangeType() == FileChangeType.DELETED) {
                // 删除文件：先按 Type/Method 的 Milvus node_id 删向量，再删图（与 EmbeddingSubmitter 主键一致）
                List<String> embeddingKeys = graphAdapter.listEmbeddingKeysForJavaFile(file.getAbsolutePath());
                embeddingStoreAdapter.deleteEmbeddingsByNodeIds(embeddingKeys);
                graphAdapter.deleteFileNodes(file.getAbsolutePath());
                continue;
            }

            // ADDED / MODIFIED：先删旧边，再重新解析写入
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
