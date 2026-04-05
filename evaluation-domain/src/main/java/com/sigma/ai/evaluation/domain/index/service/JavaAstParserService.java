package com.sigma.ai.evaluation.domain.index.service;

import com.sigma.ai.evaluation.domain.codegraph.model.ParseResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Java AST 解析服务，将 .java 源文件转换为图谱节点与关系。
 */
public interface JavaAstParserService {

    /**
     * 解析单个 Java 源文件，提取类型、方法、字段及其关系。
     *
     * @param javaFile   待解析的 Java 文件路径
     * @param sourceRoots 仓库中所有模块的 src/main/java 目录列表，供符号解析器定位跨文件引用
     * @param repoId     所属仓库 ID
     * @return 解析结果，失败时 success=false 并包含错误信息
     */
    ParseResult parse(Path javaFile, List<Path> sourceRoots, String repoId);
}
