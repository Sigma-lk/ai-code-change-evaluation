package com.sigma.ai.evaluation.trigger.consumer;

import com.sigma.ai.evaluation.domain.index.model.CommitEvent;
import com.sigma.ai.evaluation.domain.index.orchestration.IncrementalIndexOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

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

    private final IncrementalIndexOrchestrator incrementalIndexOrchestrator;

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
            incrementalIndexOrchestrator.run(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("增量索引处理失败，将触发重试: repoId={}, commitHash={}",
                    event.getRepoId(), event.getCommitHash(), e);
            throw e;
        }
    }
}
