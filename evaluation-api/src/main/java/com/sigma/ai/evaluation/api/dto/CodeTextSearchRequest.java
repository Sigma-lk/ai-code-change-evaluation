package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在已注册仓库的本地工作区内做「类 ripgrep」的文本搜索请求。
 *
 * <p>默认按<strong>字面量</strong>包含匹配（等价于常见 {@code rg -F}）；{@code useRegex=true} 时按 Java 正则对<strong>单行</strong>匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeTextSearchRequest {

    /** 必填：仓库 ID */
    private String repoId;

    /** 必填：搜索关键字或正则模式 */
    private String query;

    /**
     * 为 true 时 {@link #query} 按正则解析；为 false 时按字面量子串匹配。
     */
    @Builder.Default
    private Boolean useRegex = Boolean.FALSE;

    /**
     * 是否忽略大小写；字面量与正则均生效。
     */
    @Builder.Default
    private Boolean caseInsensitive = Boolean.FALSE;

    /**
     * 可选：限制在仓库根下的子目录内搜索（相对路径，POSIX 风格即可）。
     * 解析后必须仍在该仓库 {@code localPath} 之下。
     */
    private String subPath;

    /**
     * Glob 模式（相对搜索根），默认只扫 Java 源文件（双星号递归 + 通配扩展名，与 Java PathMatcher glob 语法一致）。
     */
    @Builder.Default
    private String glob = "**/*.java";

    /**
     * 最多返回命中条数，默认 100，上限 500。
     */
    @Builder.Default
    private Integer maxHits = 100;

    /**
     * 最多遍历的普通文件数，防止超大仓库拖死进程；默认 50000。
     */
    @Builder.Default
    private Integer maxFilesScanned = 50_000;

    /**
     * 单文件超过该字节数则跳过（避免二进制/超大文件）；默认 2MiB。
     */
    @Builder.Default
    private Long maxFileBytes = 2L * 1024 * 1024;

    /**
     * 为 true 时跳过常见构建/依赖目录名（如 {@code .git}、{@code target}、{@code node_modules} 等）。
     */
    @Builder.Default
    private Boolean skipCommonIgnoredDirs = Boolean.TRUE;
}
