package com.sigma.ai.evaluation.domain.embedding.service.impl;

import com.sigma.ai.evaluation.domain.codegraph.model.MethodNode;
import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;
import com.sigma.ai.evaluation.domain.codegraph.model.TypeNode;
import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.embedding.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link EmbeddingService} 的异步实现。
 *
 * <p>将此方法抽取为独立 Spring Bean，是为了让 {@link org.springframework.scheduling.annotation.Async @Async}
 * 通过 Spring AOP 代理生效——直接在同一个 Bean 内自调用无法触发代理拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingSubmitter implements EmbeddingService {

    private final EmbeddingStoreAdapter embeddingStoreAdapter;

    /**
     * 异步为一批 ParseResult 中的 Type 和 Method 节点生成并写入嵌入向量。
     *
     * @param results 解析结果列表
     * @param repoId  所属仓库 ID
     */
    @Async("embeddingExecutor")
    public void submitAsync(List<ParseResult> results, String repoId) {
        if (results == null || results.isEmpty()) return;
        log.debug("开始异步 Embedding 提交: resultCount={}, repoId={}", results.size(), repoId);

        int successCount = 0;
        for (ParseResult r : results) {
            try {
                for (TypeNode t : r.getTypes()) {
                    String text = t.getKind().name() + " " + t.getQualifiedName();
                    embeddingStoreAdapter.upsertEmbedding(
                            t.getQualifiedName(), "TYPE", t.getQualifiedName(), repoId, text);
                }
                for (MethodNode m : r.getMethods()) {
                    embeddingStoreAdapter.upsertEmbedding(
                            m.getId(), "METHOD", m.getId(), repoId, m.getSignature());
                }
                successCount++;
            } catch (Exception e) {
                log.error("Embedding 提交异常: file={}", r.getFilePath(), e);
            }
        }
        log.debug("Embedding 提交完成: success={}/{}", successCount, results.size());
    }
}
