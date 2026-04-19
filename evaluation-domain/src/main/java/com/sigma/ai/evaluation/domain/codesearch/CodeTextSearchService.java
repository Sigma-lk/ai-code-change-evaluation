package com.sigma.ai.evaluation.domain.codesearch;

import com.sigma.ai.evaluation.domain.codesearch.model.TextSearchResult;

/**
 * 在仓库本地工作区内执行受控的文本 / 正则行扫描（行为类似 ripgrep 的按行匹配）。
 */
public interface CodeTextSearchService {

    /**
     * 在 {@code localPath}（及可选 {@code subPath}）下遍历匹配 glob 的文件，按行查找命中。
     *
     * @param repoId                 仓库 ID
     * @param query                  字面量或正则
     * @param useRegex               是否按正则匹配单行
     * @param caseInsensitive        是否忽略大小写
     * @param subPath                可选，限制在子目录下搜索（相对路径）
     * @param glob                   glob 模式（Java PathMatcher glob，常用递归匹配 Java 源文件）
     * @param maxHits                最大命中条数
     * @param maxFilesScanned        最大扫描文件数
     * @param maxFileBytes           单文件超过则跳过
     * @param skipCommonIgnoredDirs  是否跳过 .git/target 等目录
     * @return 命中列表与截断标记
     */
    TextSearchResult search(String repoId, String query, boolean useRegex, boolean caseInsensitive,
                            String subPath, String glob, int maxHits, int maxFilesScanned, long maxFileBytes,
                            boolean skipCommonIgnoredDirs);
}
