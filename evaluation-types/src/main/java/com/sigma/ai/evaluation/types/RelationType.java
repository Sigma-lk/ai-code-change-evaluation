package com.sigma.ai.evaluation.types;

/**
 * 知识图谱关系类型枚举。
 * 用于标识 Neo4j 中各边（Relationship）的语义。
 */
public enum RelationType {

    // ===== 结构关系 =====

    /** 仓库 → 子模块 */
    CONTAINS_MODULE,

    /** 子模块 → 包 */
    CONTAINS_PACKAGE,

    /** 父包 → 子包 */
    CONTAINS_SUB,

    /** 包 → 文件 */
    CONTAINS_FILE,

    /** 文件 → 类型 */
    DEFINES_TYPE,

    /** 类型 → 方法 */
    HAS_METHOD,

    /** 类型 → 字段 */
    HAS_FIELD,

    /** 内部类 → 外部类 */
    INNER_CLASS_OF,

    // ===== 继承/实现关系 =====

    /** 类继承 */
    EXTENDS,

    /** 接口实现 */
    IMPLEMENTS,

    // ===== 调用/依赖关系 =====

    /** 方法调用 */
    CALLS,

    /** 方法读取字段 */
    READS_FIELD,

    /** 方法写入字段 */
    WRITES_FIELD,

    /** 类型依赖（字段类型/参数类型/局部变量类型引用） */
    DEPENDS_ON,

    /** 文件 import 语句 */
    IMPORTS,

    // ===== 变更追踪关系 =====

    /** 节点在指定 Commit 中发生了变更 */
    CHANGED_IN
}
