package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.controller.CodeTextSearchApi;
import com.sigma.ai.evaluation.api.dto.CodeTextSearchHit;
import com.sigma.ai.evaluation.api.dto.CodeTextSearchRequest;
import com.sigma.ai.evaluation.api.dto.CodeTextSearchResponse;
import com.sigma.ai.evaluation.domain.codesearch.CodeTextSearchService;
import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchHit;
import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 在仓库本地工作区执行类 ripgrep 的文本 / 正则行搜索。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CodeTextSearchController implements CodeTextSearchApi {

    private final CodeTextSearchService codeTextSearchService;

    /**
     * 文本搜索：精确子串或正则，返回命中文件、行号与行内容。
     *
     * @param request 搜索参数（repoId、query 必填）
     * @return 命中列表与扫描统计
     */
    @Override
    public ResponseEntity<CodeTextSearchResponse> textSearch(CodeTextSearchRequest request) {
        boolean useRegex = Boolean.TRUE.equals(request.getUseRegex());
        boolean caseInsensitive = Boolean.TRUE.equals(request.getCaseInsensitive());
        boolean skipIgnored = !Boolean.FALSE.equals(request.getSkipCommonIgnoredDirs());

        int maxHits = request.getMaxHits() == null ? 100 : request.getMaxHits();
        int maxFiles = request.getMaxFilesScanned() == null ? 50_000 : request.getMaxFilesScanned();
        long maxBytes = request.getMaxFileBytes() == null ? (2L * 1024 * 1024) : request.getMaxFileBytes();

        log.info("代码文本搜索: repoId={}, useRegex={}, caseInsensitive={}, glob={}, maxHits={}",
                request.getRepoId(), useRegex, caseInsensitive, request.getGlob(), maxHits);

        long t0 = System.currentTimeMillis();
        TextSearchResult result = codeTextSearchService.search(
                request.getRepoId(),
                request.getQuery(),
                useRegex,
                caseInsensitive,
                request.getSubPath(),
                request.getGlob(),
                maxHits,
                maxFiles,
                maxBytes,
                skipIgnored);

        List<CodeTextSearchHit> dtoHits = new ArrayList<>();
        for (TextSearchHit h : result.getHits()) {
            dtoHits.add(CodeTextSearchHit.builder()
                    .absolutePath(h.getAbsolutePath())
                    .relativePath(h.getRelativePath())
                    .lineNumber(h.getLineNumber())
                    .lineText(h.getLineText())
                    .build());
        }

        CodeTextSearchResponse body = CodeTextSearchResponse.builder()
                .repoId(request.getRepoId())
                .query(request.getQuery())
                .useRegex(useRegex)
                .caseInsensitive(caseInsensitive)
                .glob(request.getGlob() == null || request.getGlob().isBlank() ? "**/*.java" : request.getGlob())
                .hits(dtoHits)
                .scannedFiles(result.getScannedFiles())
                .truncatedByMaxHits(result.isTruncatedByMaxHits())
                .truncatedByMaxFiles(result.isTruncatedByMaxFiles())
                .elapsedMs(System.currentTimeMillis() - t0)
                .build();

        log.info("代码文本搜索完成: repoId={}, hits={}, scannedFiles={}, elapsedMs={}",
                request.getRepoId(), dtoHits.size(), result.getScannedFiles(), body.getElapsedMs());
        return ResponseEntity.ok(body);
    }
}
