package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.CodeTextSearchRequest;
import com.sigma.ai.evaluation.api.dto.CodeTextSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 代码文本搜索 HTTP 契约：在仓库工作区执行子串或正则行搜索。
 */
@RequestMapping("/api/v1")
public interface CodeTextSearchApi {

    /**
     * 文本搜索：精确子串或正则，返回命中文件、行号与行内容。
     *
     * @param request 搜索参数（repoId、query 必填）
     * @return 命中列表与扫描统计
     */
    @PostMapping("/code/text-search")
    ResponseEntity<CodeTextSearchResponse> textSearch(@RequestBody CodeTextSearchRequest request);
}
