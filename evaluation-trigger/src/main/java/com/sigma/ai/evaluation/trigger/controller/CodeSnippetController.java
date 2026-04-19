package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.controller.CodeSnippetApi;
import com.sigma.ai.evaluation.api.dto.CodeSnippetRequest;
import com.sigma.ai.evaluation.api.dto.CodeSnippetResponse;
import com.sigma.ai.evaluation.domain.codesnippet.CodeSnippetReadService;
import com.sigma.ai.evaluation.domain.codesnippet.model.CodeSnippetReadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 从已注册仓库的本地工作区读取源码片段，供下游 AI 对照真实代码。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CodeSnippetController implements CodeSnippetApi {

    private final CodeSnippetReadService codeSnippetReadService;

    /**
     * 按仓库 ID 与文件路径返回 UTF-8 文本片段。
     *
     * @param request repoId、filePath、startLine、endLine 必填
     * @return 片段与路径元数据
     */
    @Override
    public ResponseEntity<CodeSnippetResponse> readSnippet(CodeSnippetRequest request) {
        log.info("代码片段请求: repoId={}, filePath={}, lineRange={}-{}",
                request.getRepoId(),
                request.getFilePath(),
                request.getStartLine(),
                request.getEndLine());

        long t0 = System.currentTimeMillis();
        CodeSnippetReadResult r = codeSnippetReadService.readSnippet(
                request.getRepoId(),
                request.getFilePath(),
                request.getStartLine(),
                request.getEndLine(),
                request.getMaxReturnChars());

        CodeSnippetResponse body = CodeSnippetResponse.builder()
                .repoId(r.getRepoId())
                .resolvedAbsolutePath(r.getResolvedAbsolutePath())
                .relativePath(r.getRelativePath())
                .encoding(r.getEncoding())
                .totalLinesInFile(r.getTotalLinesInFile())
                .returnedStartLine(r.getReturnedStartLine())
                .returnedEndLine(r.getReturnedEndLine())
                .truncated(r.isTruncated())
                .truncationNote(r.getTruncationNote())
                .content(r.getContent())
                .build();

        log.info("代码片段响应: repoId={}, elapsedMs={}, chars={}",
                request.getRepoId(), System.currentTimeMillis() - t0,
                r.getContent() == null ? 0 : r.getContent().length());
        return ResponseEntity.ok(body);
    }
}
