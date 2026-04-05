package com.sigma.ai.evaluation.trigger.scheduler;

import com.sigma.ai.evaluation.domain.index.service.FullIndexService;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 全量索引定时调度器。
 * 每天凌晨 2 点对所有 ACTIVE 仓库触发一次全量重建，保持图谱完整性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexScheduler {

    private final RepositoryPort repositoryPort;
    private final FullIndexService fullIndexService;

    /**
     * 定时全量索引任务。
     * cron 表达式：每天 02:00 触发。
     * 实际部署时可通过配置文件 {@code index.scheduler.cron} 覆盖。
     */
    @Scheduled(cron = "${index.scheduler.cron:0 0 2 * * ?}")
    public void scheduledFullIndex() {
        List<RepositoryInfo> repos = repositoryPort.findAllActive();
        log.info("定时全量索引开始，共 {} 个仓库", repos.size());

        for (RepositoryInfo repo : repos) {
            try {
                log.info("触发全量索引: repoId={}, name={}", repo.getId(), repo.getName());
                fullIndexService.runFullIndex(repo.getId());
            } catch (Exception e) {
                log.error("仓库全量索引异常（已跳过）: repoId={}", repo.getId(), e);
            }
        }

        log.info("定时全量索引调度完毕");
    }
}
