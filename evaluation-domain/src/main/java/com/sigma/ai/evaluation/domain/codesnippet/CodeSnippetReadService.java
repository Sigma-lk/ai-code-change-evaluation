package com.sigma.ai.evaluation.domain.codesnippet;

import com.sigma.ai.evaluation.domain.codesnippet.model.CodeSnippetReadResult;

/**
 * 从已注册仓库的本地工作区读取源文件片段，供 HTTP 层或编排服务调用。
 */
public interface CodeSnippetReadService {

    /**
     * 在仓库 {@code localPath} 下解析 {@code filePath}，按行区间读取 UTF-8 文本片段。
     *
     * @param repoId          仓库 ID
     * @param filePath        相对或绝对路径，解析后须落在该仓库本地目录下
     * @param startLine       必填，起始行（含，从 1 起）
     * @param endLine         必填，结束行（含）
     * @param maxReturnChars  正文最大字符数；null 时使用服务端默认
     * @return 片段内容与元数据
     */
    CodeSnippetReadResult readSnippet(String repoId, String filePath,
                                      Integer startLine, Integer endLine,
                                      Integer maxReturnChars);
}
