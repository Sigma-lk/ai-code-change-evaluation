package com.sigma.ai.evaluation.trigger.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub Webhook 处理完成后的简要响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubWebhookAck {

    private boolean indexed;

    /** 为 true 时表示该 commit 此前已处理，未重复写图 */
    private boolean skippedDuplicate;

    private String repoId;

    private String commitHash;

    private int changedJavaFileCount;

    private int evidenceNodeCount;

    private int lineInsertions;

    private int lineDeletions;
}
