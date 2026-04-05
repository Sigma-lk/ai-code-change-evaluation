package com.sigma.ai.evaluation.domain.codegraph.model;

import com.sigma.ai.evaluation.types.TypeKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j Type 节点，统一表示类、接口、枚举和注解，通过 kind 区分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeNode {

    /**
     * 唯一标识，使用全限定名。
     * 跨仓库全量重建时此键保持稳定，避免数字 id 漂移。
     */
    private String qualifiedName;

    /** 简单名称，如 UserService */
    private String simpleName;

    /** 类型种类：CLASS / INTERFACE / ENUM / ANNOTATION */
    private TypeKind kind;

    /** 访问修饰符：public / protected / private / package-private */
    private String accessModifier;

    /** 是否抽象 */
    private boolean isAbstract;

    /** 是否 final */
    private boolean isFinal;

    /** 是否 static（内部静态类） */
    private boolean isStatic;

    /** 所在文件绝对路径 */
    private String filePath;

    /** 声明起始行 */
    private int lineStart;

    /** 声明结束行 */
    private int lineEnd;
}
