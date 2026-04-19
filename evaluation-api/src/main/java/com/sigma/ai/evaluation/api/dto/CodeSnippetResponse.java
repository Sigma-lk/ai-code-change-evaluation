package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仓库内文件片段读取响应，供下游 AI 直接对照源码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeSnippetResponse {

    private String repoId;

    /** 解析后实际读取的绝对路径（与图谱中 filePath 对齐时多为绝对路径） */
    private String resolvedAbsolutePath;

    /**
     * 相对仓库 {@code localPath} 的路径（POSIX 风格分隔符），便于展示。
     */
    private String relativePath;

    /** 文本编码，当前固定为 UTF-8 */
    @Builder.Default
    private String encoding = "UTF-8";

    /**
     * 文件总行数；在仅按行区间流式读取且未全文件扫描时可能为 null。
     */
    private Integer totalLinesInFile;

    /** 本响应 {@link #content} 覆盖的首行号（从 1 起） */
    private Integer returnedStartLine;

    /** 本响应 {@link #content} 覆盖的末行号（从 1 起） */
    private Integer returnedEndLine;

    /** 是否因行数/字符上限被截断 */
    @Builder.Default
    private Boolean truncated = Boolean.FALSE;

    /** 截断或降级时的说明（如仅返回前 N 行） */
    private String truncationNote;

    /** 文件正文片段 */
    private String content;
}
