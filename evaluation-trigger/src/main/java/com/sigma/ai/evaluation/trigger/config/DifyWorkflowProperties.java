package com.sigma.ai.evaluation.trigger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dify 工作流调用配置：将变更证据 JSON 作为输入变量投递。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dify.workflow")
public class DifyWorkflowProperties {

    /** 为 false 时不发起 HTTP 调用（仅打日志） */
    private boolean enabled = false;

    /** 如 https://api.dify.ai ，勿带尾斜杠 */
    private String baseUrl = "https://api.dify.ai";

    /** 工作流应用 API Key（Bearer） */
    private String apiKey = "";

    /** Dify 请求中的 user 标识 */
    private String user = "github-webhook";

    /**
     * 工作流输入里承载整包 JSON 的变量名，须与工作流编排中定义的变量名一致。
     */
    private String inputKey = "change_payload";

    /** 仅为前 N 个节点计算 diffSnippet（控制体积）；其余节点 diffSnippet 为空串 */
    private int maxSnippetNodes = 120;

    /** 片段上下扩展行数 */
    private int snippetPaddingLines = 6;

    /** 每个片段最大字符 */
    private int maxSnippetChars = 12000;
}
