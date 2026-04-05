package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Java 文件的 AST 解析结果，包含从该文件中提取的所有节点与关系。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /** 被解析的文件绝对路径 */
    private String filePath;

    /** 解析是否成功 */
    private boolean success;

    /** 解析失败时的错误信息 */
    private String errorMessage;

    /** 解析出的包节点（一个文件对应一个包） */
    private PackageNode packageNode;

    /** 解析出的文件节点 */
    private JavaFileNode javaFileNode;

    /** 解析出的类型节点列表 */
    @Builder.Default
    private List<TypeNode> types = new ArrayList<>();

    /** 解析出的方法节点列表 */
    @Builder.Default
    private List<MethodNode> methods = new ArrayList<>();

    /** 解析出的字段节点列表 */
    @Builder.Default
    private List<FieldNode> fields = new ArrayList<>();

    /** 解析出的所有图关系列表 */
    @Builder.Default
    private List<GraphRelation> relations = new ArrayList<>();
}
