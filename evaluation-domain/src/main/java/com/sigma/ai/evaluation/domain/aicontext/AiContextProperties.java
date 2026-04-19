package com.sigma.ai.evaluation.domain.aicontext;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 分析上下文组装的防护与默认参数（可由 application.yml 覆盖）。
 */
@Component
@Data
@ConfigurationProperties(prefix = "ai.context")
public class AiContextProperties {

    /** semanticQueries 最大条数 */
    private int maxSemanticQueries = 8;

    private int maxNodesHardCap = 2000;

    private int maxEdgesHardCap = 8000;

    private int maxPathsHardCap = 200;

    private long assembleTimeoutMs = 60_000L;
}
