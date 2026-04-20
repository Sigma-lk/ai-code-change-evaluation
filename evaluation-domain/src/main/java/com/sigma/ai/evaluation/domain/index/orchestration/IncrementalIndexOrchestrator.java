package com.sigma.ai.evaluation.domain.index.orchestration;

import com.sigma.ai.evaluation.domain.index.model.CommitEvent;
import com.sigma.ai.evaluation.domain.index.model.IncrementalIndexResult;

/**
 * 增量索引编排：幂等、Git 同步、diff、解析写图、Commit 记录。
 * <p>供 Kafka 消费者与 GitHub Webhook 等入口复用。
 */
public interface IncrementalIndexOrchestrator {

    /**
     * 执行一次增量索引。
     *
     * @param event 提交事件（须含已注册的 {@code repoId}）
     * @return 编排结果；若已处理过则 {@link IncrementalIndexResult#isSkippedAlreadyProcessed()} 为 true
     */
    IncrementalIndexResult run(CommitEvent event);
}
