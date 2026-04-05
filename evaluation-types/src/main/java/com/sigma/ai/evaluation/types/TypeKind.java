package com.sigma.ai.evaluation.types;

/**
 * Java 类型声明种类枚举，对应 Neo4j Type 节点的 kind 属性。
 */
public enum TypeKind {

    /** 普通类 */
    CLASS,

    /** 接口 */
    INTERFACE,

    /** 枚举 */
    ENUM,

    /** 注解 */
    ANNOTATION
}
