package com.sigma.ai.evaluation.domain.index.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件扫描服务，递归遍历仓库目录中的 .java 文件。
 */
public interface FileWalkerService {

    /**
     * 递归扫描指定目录下所有 .java 文件。
     *
     * @param rootDir 仓库根目录
     * @return 所有 .java 文件路径列表
     */
    List<Path> walkJavaFiles(Path rootDir);

    /**
     * 计算文件内容的 MD5 checksum。
     *
     * @param file 文件路径
     * @return HEX 编码的 MD5 字符串
     */
    String computeChecksum(Path file);
}
