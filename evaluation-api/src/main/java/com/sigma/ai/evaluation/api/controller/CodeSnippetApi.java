package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.CodeSnippetRequest;
import com.sigma.ai.evaluation.api.dto.CodeSnippetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 代码片段读取 HTTP 契约：从已注册仓库工作区读取 UTF-8 源码片段。
 */
@RequestMapping("/api/v1")
public interface CodeSnippetApi {

    /**
     * 按仓库 ID 与文件路径返回 UTF-8 文本片段。
     *
     * @param request repoId、filePath、startLine、endLine 必填
     * @return 片段与路径元数据
     */
    @PostMapping("/code/snippet")
    ResponseEntity<CodeSnippetResponse> readSnippet(@RequestBody CodeSnippetRequest request);
}
