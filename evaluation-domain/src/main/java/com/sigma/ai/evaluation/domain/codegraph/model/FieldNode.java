package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Field 节点，表示 Java 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldNode {

    /**
     * 唯一标识，格式：{ownerQualifiedName}#{fieldName}。
     * 示例：com.example.UserService#userRepository
     */
    private String id;

    /** 字段所属类的全限定名 */
    private String ownerQualifiedName;

    /** 字段简名 */
    private String simpleName;

    /** 字段类型简名 */
    private String typeName;

    /** 字段类型全限定名（可能未解析，为空） */
    private String typeQualifiedName;

    /** 访问修饰符 */
    private String accessModifier;

    /** 是否静态 */
    private boolean isStatic;

    /** 是否 final */
    private boolean isFinal;

    /** 所在文件绝对路径 */
    private String filePath;

    /** 字段声明所在行 */
    private int lineNo;
}
