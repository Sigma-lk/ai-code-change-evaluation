package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 读取仓库工作区内源文件片段的请求体。
 *
 * <p>{@code filePath} 可为<strong>相对仓库根目录（localPath）</strong>的路径，也可为<strong>绝对路径</strong>，
 * 但解析后必须仍落在该仓库 {@code localPath} 之下，否则拒绝（防目录穿越）。
 *
 * <p>必须同时提供 {@code startLine} 与 {@code endLine}（从 1 起算、闭区间），按行流式读取；不支持不传行号的整文件读取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeSnippetRequest {

    /** 必填：仓库 ID（与索引注册一致） */
    private String repoId;

    /**
     * 必填：目标文件路径。
     * 相对路径时相对于仓库 {@code localPath}；绝对路径时必须位于该目录之下。
     */
    private String filePath;

    /**
     * 必填：起始行号（含），从 1 起算；与 {@link #endLine} 必须同时出现。
     */
    private Integer startLine;

    /**
     * 必填：结束行号（含），从 1 起算。
     */
    private Integer endLine;

    /**
     * 返回正文中允许的最大字符数（按 Java {@link String#length()} 计），用于防止超大响应。
     * 默认 262144（256Ki 字符量级）。
     */
    @Builder.Default
    private Integer maxReturnChars = 262_144;
}
