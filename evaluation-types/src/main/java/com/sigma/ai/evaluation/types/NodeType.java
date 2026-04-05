package com.sigma.ai.evaluation.types;

/**
 * 知识图谱节点类型枚举。
 * 用于标识 Neo4j 中各节点所属的标签（Label）。
 */
public enum NodeType {

    /** Git 仓库根节点 */
    REPOSITORY,

    /** Maven 子模块节点 */
    MODULE,

    /** Java 包节点 */
    PACKAGE,

    /** Java 源文件节点 */
    JAVA_FILE,

    /** Java 类型节点（类/接口/枚举/注解，由 kind 区分） */
    TYPE,

    /** Java 方法节点（含构造器） */
    METHOD,

    /** Java 字段节点 */
    FIELD,

    /** Git 提交节点 */
    COMMIT
}
