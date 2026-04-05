package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Method 节点，表示 Java 方法（含构造器）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodNode {

    /**
     * 唯一标识，格式：{ownerQualifiedName}#{methodName}({paramType1},{paramType2}...)。
     * 示例：com.example.UserService#findById(Long)
     */
    private String id;

    /** 方法所属类的全限定名 */
    private String ownerQualifiedName;

    /** 方法简名 */
    private String simpleName;

    /**
     * 完整签名，含返回类型。
     * 示例：public User findById(Long id)
     */
    private String signature;

    /** 返回类型简名 */
    private String returnType;

    /** 访问修饰符 */
    private String accessModifier;

    /** 是否静态 */
    private boolean isStatic;

    /** 是否抽象 */
    private boolean isAbstract;

    /** 是否构造器 */
    private boolean isConstructor;

    /** 所在文件绝对路径 */
    private String filePath;

    /** 方法声明起始行 */
    private int lineStart;

    /** 方法声明结束行 */
    private int lineEnd;
}
