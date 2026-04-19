package com.sigma.ai.evaluation.domain.index.service.impl;

import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.model.RepositoryNode;
import com.sigma.ai.evaluation.domain.codegraph.service.CodeGraphService;
import com.sigma.ai.evaluation.domain.embedding.service.EmbeddingService;
import com.sigma.ai.evaluation.domain.index.adapter.IndexTaskPort;
import com.sigma.ai.evaluation.domain.index.adapter.ParseErrorPort;
import com.sigma.ai.evaluation.domain.index.model.IndexTask;
import com.sigma.ai.evaluation.domain.index.service.FileWalkerService;
import com.sigma.ai.evaluation.domain.index.service.FullIndexService;
import com.sigma.ai.evaluation.domain.index.service.JavaAstParserService;
import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 全量索引服务实现。
 *
 * <p>执行顺序：
 * <ol>
 *   <li>从 {@link RepositoryPort} 查询仓库注册信息</li>
 *   <li>在 PostgreSQL 创建 RUNNING 状态的索引任务</li>
 *   <li>通过 {@link GitAdapter} 执行 clone/pull</li>
 *   <li>扫描所有 .java 文件，跳过 checksum 未变化的文件（幂等）</li>
 *   <li>逐文件 AST 解析，每批 {@value #BATCH_SIZE} 个节点委托 {@link CodeGraphService} 批量写入</li>
 *   <li>异步委托 {@link EmbeddingService} 写入向量</li>
 *   <li>更新索引任务状态为 SUCCESS 或 FAIL</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullIndexServiceImpl implements FullIndexService {

    private static final int BATCH_SIZE = 500;

    private final RepositoryPort repositoryPort;
    private final IndexTaskPort indexTaskPort;
    private final ParseErrorPort parseErrorPort;
    private final GitAdapter gitAdapter;
    private final FileWalkerService fileWalkerService;
    private final JavaAstParserService javaAstParserService;
    private final CodeGraphService codeGraphService;
    private final EmbeddingService embeddingService;

    @Override
    public void runFullIndex(String repoId) {
        RepositoryInfo repo = repositoryPort.findById(repoId);
        if (repo == null) {
            log.error("全量索引中止：仓库未注册, repoId={}", repoId);
            throw ResourceNotFoundException.repositoryNotFound(repoId);
        }

        // 插入全量索引构建任务
        IndexTask task = indexTaskPort.createTask(IndexTask.builder()
                .repoId(repoId)
                .taskType(TaskType.FULL)
                .status(TaskStatus.RUNNING)
                .startedAt(Instant.now())
                .build());
        log.info("全量索引任务启动, taskId={}, repoId={}", task.getId(), repoId);

        try {
            log.info("开始拉取代码, url={}, branch={}, localPath={}",
                    repo.getCloneUrl(), repo.getBranch(), repo.getLocalPath());
            gitAdapter.cloneOrPull(repo.getCloneUrl(), repo.getBranch(), repo.getLocalPath());

            Path repoPath = Path.of(repo.getLocalPath());

            // 写入仓库根节点
            codeGraphService.writeRepositoryNode(RepositoryNode.builder()
                    .id(repo.getId())
                    .name(repo.getName())
                    .url(repo.getCloneUrl())
                    .defaultBranch(repo.getBranch())
                    .updatedAt(Instant.now())
                    .build());

            // 收集以 src/main/java 结尾且是目录的路径
            List<Path> sourceRoots = findSourceRoots(repoPath);
            log.info("找到 {} 个源根目录", sourceRoots.size());

            // 收集.java文件
            List<Path> javaFiles = fileWalkerService.walkJavaFiles(repoPath);
            log.info("共扫描到 {} 个 .java 文件", javaFiles.size());

            runParseAndWrite(javaFiles, sourceRoots, repoId, task.getId());

            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.SUCCESS, null);
            log.info("全量索引任务完成, taskId={}, repoId={}", task.getId(), repoId);

        } catch (Exception e) {
            log.error("全量索引任务失败, taskId={}, repoId={}", task.getId(), repoId, e);
            indexTaskPort.updateTaskStatus(task.getId(), TaskStatus.FAIL, e.getMessage());
            throw IndexTaskException.fullIndexFailed(e);
        }
    }

    /**
     * 递归查找仓库中所有 Maven 模块的 src/main/java 目录。
     */
    private List<Path> findSourceRoots(Path repoPath) {
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoPath)) {
            stream.filter(p -> p.endsWith(Path.of("src/main/java")))
                    .filter(Files::isDirectory)
                    .forEach(roots::add);
        } catch (IOException e) {
            log.warn("查找源根目录失败: {}", repoPath, e);
        }
        return roots;
    }

    /**
     * 解析所有 Java 文件并分批写入 Neo4j 与向量库。
     * 每当累积节点数达到 BATCH_SIZE 时立即 flush，防止内存过大。
     */
    private void runParseAndWrite(List<Path> javaFiles, List<Path> sourceRoots,
                                   String repoId, Long taskId) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<ParseResult> buffer = new ArrayList<>();
        int bufferedNodeCount = 0;

        for (Path javaFile : javaFiles) {
            // checksum 幂等检查，已存储且未变化则跳过
            String newChecksum = fileWalkerService.computeChecksum(javaFile);
            String storedChecksum = codeGraphService.getFileChecksum(javaFile.toString());
            if (newChecksum.equals(storedChecksum)) {
                log.debug("文件 checksum 未变化，跳过解析: {}", javaFile);
                continue;
            }

            // 解析Java源文件
            ParseResult result = javaAstParserService.parse(javaFile, sourceRoots, repoId);

            if (!result.isSuccess()) {
                failCount.incrementAndGet();
                parseErrorPort.record(taskId, javaFile.toString(), "PARSE_ERROR", result.getErrorMessage());
                log.warn("文件解析失败: {}, error={}", javaFile, result.getErrorMessage());
                continue;
            }

            successCount.incrementAndGet();
            buffer.add(result);
            bufferedNodeCount += result.getTypes().size()
                    + result.getMethods().size()
                    + result.getFields().size();

            if (bufferedNodeCount >= BATCH_SIZE) {
                List<ParseResult> batchSnapshot = new ArrayList<>(buffer);
                codeGraphService.batchWriteParseResults(batchSnapshot);
                // 每批写入后立即异步提交 Embedding，避免全部积压到最后
                embeddingService.submitAsync(batchSnapshot, repoId);
                buffer.clear();
                bufferedNodeCount = 0;
            }
        }

        // flush 剩余
        if (!buffer.isEmpty()) {
            codeGraphService.batchWriteParseResults(buffer);
            embeddingService.submitAsync(new ArrayList<>(buffer), repoId);
        }

        log.info("解析写入完成: success={}, fail={}", successCount.get(), failCount.get());
    }
}
