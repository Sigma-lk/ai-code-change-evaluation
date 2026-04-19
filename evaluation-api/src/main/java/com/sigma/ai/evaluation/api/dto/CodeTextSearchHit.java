package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次文本搜索命中：文件 + 行号 + 行内容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeTextSearchHit {

    /** 命中文件的绝对路径 */
    private String absolutePath;

    /** 相对仓库 {@code localPath} 的路径（POSIX 分隔符） */
    private String relativePath;

    /** 行号，从 1 起 */
    private int lineNumber;

    /** 该行 UTF-8 文本（过长时截断） */
    private String lineText;
}
