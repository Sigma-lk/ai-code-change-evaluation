package com.sigma.ai.evaluation.api.scheduler;

/**
 * 全量索引定时调度契约：由触发层在计划时间点对所有活跃仓库触发全量重建。
 *
 * <p>具体 cron 与线程池由实现侧（如 Spring {@code @Scheduled}）配置；本接口仅描述可调度的业务入口。
 */
public interface IndexSchedulerApi {

    /**
     * 执行一次定时全量索引：遍历当前 ACTIVE 仓库并依次触发全量索引。
     *
     * <p>单仓失败时不应中断其余仓库的处理；实现类负责记录日志与异常吞并策略。
     */
    void scheduledFullIndex();
}
