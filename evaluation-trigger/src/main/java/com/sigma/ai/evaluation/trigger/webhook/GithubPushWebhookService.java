package com.sigma.ai.evaluation.trigger.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigma.ai.evaluation.domain.changeevidence.ChangeEvidenceAssembler;
import com.sigma.ai.evaluation.domain.changeevidence.ChangeEvidenceDocument;
import com.sigma.ai.evaluation.domain.index.model.CommitEvent;
import com.sigma.ai.evaluation.domain.index.model.IncrementalIndexResult;
import com.sigma.ai.evaluation.domain.index.orchestration.IncrementalIndexOrchestrator;
import com.sigma.ai.evaluation.domain.repository.adapter.RepositoryPort;
import com.sigma.ai.evaluation.domain.repository.model.RepositoryInfo;
import com.sigma.ai.evaluation.trigger.config.DifyWorkflowProperties;
import com.sigma.ai.evaluation.trigger.config.GithubWebhookProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 处理 GitHub {@code push}：验签、对齐 repoId、增量索引、变更证据组装；Dify 工作流在独立线程池异步投递，HTTP 不等待其结束。
 */
@Slf4j
@Service
public class GithubPushWebhookService {

    private final ObjectMapper objectMapper;
    private final GithubWebhookProperties githubWebhookProperties;
    private final DifyWorkflowProperties difyWorkflowProperties;
    private final RepositoryPort repositoryPort;
    private final IncrementalIndexOrchestrator incrementalIndexOrchestrator;
    private final ChangeEvidenceAssembler changeEvidenceAssembler;
    private final DifyWorkflowClient difyWorkflowClient;
    private final Executor difyWorkflowExecutor;

    /**
     * 由 Spring 注入；{@code Executor} 使用 {@code difyWorkflowExecutor} 线程池，避免与索引、向量等线程池混淆。
     */
    public GithubPushWebhookService(
            ObjectMapper objectMapper,
            GithubWebhookProperties githubWebhookProperties,
            DifyWorkflowProperties difyWorkflowProperties,
            RepositoryPort repositoryPort,
            IncrementalIndexOrchestrator incrementalIndexOrchestrator,
            ChangeEvidenceAssembler changeEvidenceAssembler,
            DifyWorkflowClient difyWorkflowClient,
            @Qualifier("difyWorkflowExecutor") Executor difyWorkflowExecutor) {
        this.objectMapper = objectMapper;
        this.githubWebhookProperties = githubWebhookProperties;
        this.difyWorkflowProperties = difyWorkflowProperties;
        this.repositoryPort = repositoryPort;
        this.incrementalIndexOrchestrator = incrementalIndexOrchestrator;
        this.changeEvidenceAssembler = changeEvidenceAssembler;
        this.difyWorkflowClient = difyWorkflowClient;
        this.difyWorkflowExecutor = difyWorkflowExecutor;
    }

    /**
     * 处理 push 事件。
     *
     * @param rawBody        原始 JSON 字节（用于签名校验）
     * @param signature256   {@code X-Hub-Signature-256}
     * @param githubEvent    {@code X-GitHub-Event}
     * @return 200 与摘要（不等待 Dify 工作流完成）；非 push 返回 204；签名校验失败 401；仓库未对齐 404
     */
    public ResponseEntity<GithubWebhookAck> handlePush(byte[] rawBody, String signature256, String githubEvent) {
        if (githubEvent == null || !"push".equalsIgnoreCase(githubEvent.trim())) {
            log.debug("忽略非 push 的 GitHub 事件: {}", githubEvent);
            return ResponseEntity.noContent().build();
        }

        String secret = githubWebhookProperties.getSecret();
        if (secret != null && !secret.isBlank()) {
            if (signature256 == null
                    || !GithubWebhookSignatureVerifier.isValid(secret, rawBody, signature256)) {
                log.warn("GitHub Webhook 签名校验失败");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            log.warn("github.webhook.secret 未配置，跳过签名校验（不推荐用于生产）");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Webhook JSON 解析失败", e);
            return ResponseEntity.badRequest().build();
        }

        String cloneUrl = firstNonBlank(text(root, "repository", "clone_url"), text(root, "repository", "ssh_url"));
        if (cloneUrl == null || cloneUrl.isBlank()) {
            log.warn("payload 缺少 repository.clone_url / ssh_url");
            return ResponseEntity.badRequest().build();
        }

        Optional<RepositoryInfo> repoOpt = repositoryPort.findActiveByCloneUrl(cloneUrl);
        if (repoOpt.isEmpty()) {
            log.warn("未找到与 clone_url 匹配的活跃仓库: normalizedKey 已用于内部比对, raw={}", cloneUrl);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        RepositoryInfo repo = repoOpt.get();

        String before = text(root, "before");
        String after = text(root, "after");
        if (after == null || after.isBlank()) {
            log.warn("payload 缺少 after");
            return ResponseEntity.badRequest().build();
        }

        String ref = text(root, "ref");
        String branch = parseBranchFromRef(ref);
        if (branch != null && repo.getBranch() != null && !repo.getBranch().equals(branch)) {
            log.warn("推送分支与仓库登记 branch 不一致: repoId={}, registered={}, push={}",
                    repo.getId(), repo.getBranch(), branch);
        }

        String pusherName = parsePusher(root);

        List<String> commitMessages = new ArrayList<>();
        JsonNode commits = root.get("commits");
        if (commits != null && commits.isArray()) {
            for (JsonNode c : commits) {
                if (c != null && c.hasNonNull("message")) {
                    commitMessages.add(c.get("message").asText());
                }
            }
        }

        TipCommitAuthor tipAuthor = parseTipCommitAuthor(root, after);

        CommitEvent event = CommitEvent.builder()
                .repoId(repo.getId())
                .commitHash(after)
                .branch(branch != null ? branch : repo.getBranch())
                .pusher(pusherName)
                .parentCommitHash(before)
                .build();

        log.info("GitHub push 开始增量索引: repoId={}, after={}, before={}, ref={}, files(payload commits)={}",
                repo.getId(), after, before, ref, commitMessages.size());

        IncrementalIndexResult indexResult = incrementalIndexOrchestrator.run(event);

        if (indexResult.isSkippedAlreadyProcessed()) {
            log.info("提交已处理过，跳过写图与 Dify: repoId={}, commit={}", repo.getId(), after);
            GithubWebhookAck ack = GithubWebhookAck.builder()
                    .indexed(false)
                    .skippedDuplicate(true)
                    .repoId(repo.getId())
                    .commitHash(after)
                    .changedJavaFileCount(0)
                    .evidenceNodeCount(0)
                    .lineInsertions(0)
                    .lineDeletions(0)
                    .build();
            return ResponseEntity.ok(ack);
        }

        ChangeEvidenceDocument evidence = changeEvidenceAssembler.assemble(
                repo.getId(),
                indexResult,
                before,
                after,
                ref,
                branch != null ? branch : repo.getBranch(),
                commitMessages,
                cloneUrl,
                tipAuthor.name(),
                tipAuthor.email(),
                difyWorkflowProperties.getMaxSnippetNodes(),
                difyWorkflowProperties.getSnippetPaddingLines(),
                difyWorkflowProperties.getMaxSnippetChars());

        int nodeCount = evidence.getNodes() != null ? evidence.getNodes().size() : 0;
        int ins = indexResult.getLineStats() != null ? indexResult.getLineStats().getTotalInsertions() : 0;
        int del = indexResult.getLineStats() != null ? indexResult.getLineStats().getTotalDeletions() : 0;

        log.info("变更统计 repoId={}, commitHash={}, javaFiles={}, lineIns={}, lineDel={}, astNodes={}, commitMessagesCount={}",
                repo.getId(),
                after,
                indexResult.getChangedJavaFiles() != null ? indexResult.getChangedJavaFiles().size() : 0,
                ins,
                del,
                nodeCount,
                commitMessages.size());

        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            log.error("变更证据序列化失败", e);
            throw new IllegalStateException("变更证据序列化失败", e);
        }

        difyWorkflowExecutor.execute(() -> {
            try {
                difyWorkflowClient.runWorkflowBlocking(evidenceJson);
            } catch (Exception e) {
                log.error("Dify 工作流投递失败（异步，图谱增量已成功），repoId={}, commit={}", repo.getId(), after, e);
            }
        });

        GithubWebhookAck ack = GithubWebhookAck.builder()
                .indexed(true)
                .skippedDuplicate(false)
                .repoId(repo.getId())
                .commitHash(after)
                .changedJavaFileCount(indexResult.getChangedJavaFiles() != null ? indexResult.getChangedJavaFiles().size() : 0)
                .evidenceNodeCount(nodeCount)
                .lineInsertions(ins)
                .lineDeletions(del)
                .build();
        return ResponseEntity.ok(ack);
    }

    private static String parsePusher(JsonNode root) {
        JsonNode pusher = root.get("pusher");
        if (pusher == null || pusher.isNull()) {
            return "unknown";
        }
        if (pusher.isTextual()) {
            return pusher.asText();
        }
        if (pusher.hasNonNull("name")) {
            return pusher.get("name").asText();
        }
        return "unknown";
    }

    private static String parseBranchFromRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        return ref;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n != null && n.isTextual() ? n.asText() : null;
    }

    private static String text(JsonNode root, String parent, String child) {
        JsonNode p = root.get(parent);
        if (p == null || !p.isObject()) {
            return null;
        }
        JsonNode c = p.get(child);
        return c != null && c.isTextual() ? c.asText() : null;
    }

    /**
     * GitHub push payload 中与 {@code after} 对应的 tip 提交作者（优先 {@code head_commit}，其次 {@code commits[]}）。
     */
    private record TipCommitAuthor(String name, String email) {
    }

    private static TipCommitAuthor parseTipCommitAuthor(JsonNode root, String afterSha) {
        if (afterSha == null || afterSha.isBlank()) {
            return new TipCommitAuthor(null, null);
        }
        JsonNode head = root.get("head_commit");
        if (head != null && head.isObject()) {
            String headId = text(head, "id");
            if (headId == null || afterSha.equalsIgnoreCase(headId)) {
                TipCommitAuthor fromHead = tipAuthorFromGitAuthorNode(head.get("author"));
                if (fromHead.name() != null || fromHead.email() != null) {
                    return fromHead;
                }
            }
        }
        JsonNode commits = root.get("commits");
        if (commits != null && commits.isArray()) {
            for (JsonNode c : commits) {
                if (c == null || !c.isObject()) {
                    continue;
                }
                String id = text(c, "id");
                if (id != null && afterSha.equalsIgnoreCase(id)) {
                    return tipAuthorFromGitAuthorNode(c.get("author"));
                }
            }
        }
        if (head != null && head.isObject()) {
            return tipAuthorFromGitAuthorNode(head.get("author"));
        }
        return new TipCommitAuthor(null, null);
    }

    private static TipCommitAuthor tipAuthorFromGitAuthorNode(JsonNode author) {
        if (author == null || author.isNull() || !author.isObject()) {
            return new TipCommitAuthor(null, null);
        }
        String name = author.hasNonNull("name") && author.get("name").isTextual()
                ? blankToNull(author.get("name").asText())
                : null;
        String email = author.hasNonNull("email") && author.get("email").isTextual()
                ? blankToNull(author.get("email").asText())
                : null;
        return new TipCommitAuthor(name, email);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
