package com.sigma.ai.evaluation.domain.aicontext;

/**
 * 组装面向下游 LLM 的结构化分析上下文（图 + 语义 + 种子 + 摘要）。
 */
public interface AiContextAssemblyService {

    /**
     * 根据入参编排 Neo4j 子图展开与 Milvus 语义检索，返回完整证据包。
     *
     * @param input 与 HTTP 契约对齐的领域入参
     * @return 结构化出参；校验失败时应由调用方先处理，本方法不返回 4xx
     */
    AiContextAssemblyOutput assemble(AiContextAssemblyInput input);
}
