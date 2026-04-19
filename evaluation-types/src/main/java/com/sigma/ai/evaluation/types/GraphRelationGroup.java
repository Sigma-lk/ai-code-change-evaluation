package com.sigma.ai.evaluation.types;

import java.util.EnumSet;
import java.util.Set;

/**
 * 图多跳展开时的关系分组，映射到 {@link RelationType} 白名单子集。
 *
 * <p>默认策略（分析上下文接口）：仅 {@link #CALL_CHAIN} 参与展开；
 * {@link #IMPORTS} 与 {@link #STRUCTURE} 默认关闭，避免子图爆炸。
 */
public enum GraphRelationGroup {

    /** 方法调用链：{@link RelationType#CALLS} */
    CALL_CHAIN,

    /** 类型层次：{@link RelationType#EXTENDS}、{@link RelationType#IMPLEMENTS} */
    TYPE_HIERARCHY,

    /** 字段访问：{@link RelationType#READS_FIELD}、{@link RelationType#WRITES_FIELD} */
    FIELD_ACCESS,

    /** 类型依赖：{@link RelationType#DEPENDS_ON} */
    TYPE_DEPENDS,

    /** import 边：{@link RelationType#IMPORTS} */
    IMPORTS,

    /** 目录/模块结构：{@link RelationType#CONTAINS_MODULE} 等 CONTAINS_* */
    STRUCTURE;

    /**
     * 与主接口约定一致的默认展开分组（不含 IMPORTS / STRUCTURE）。
     */
    public static Set<GraphRelationGroup> defaultExpansionGroups() {
        return EnumSet.of(CALL_CHAIN, TYPE_HIERARCHY, FIELD_ACCESS, TYPE_DEPENDS);
    }

    /**
     * 将分组解析为具体 {@link RelationType} 集合（用于 Cypher 白名单）。
     *
     * @param groups 请求指定的分组
     * @return 去重后的关系类型集合
     */
    public static Set<RelationType> toRelationTypes(Set<GraphRelationGroup> groups) {
        EnumSet<RelationType> set = EnumSet.noneOf(RelationType.class);
        if (groups == null || groups.isEmpty()) {
            return set;
        }
        for (GraphRelationGroup g : groups) {
            switch (g) {
                case CALL_CHAIN -> set.add(RelationType.CALLS);
                case TYPE_HIERARCHY -> {
                    set.add(RelationType.EXTENDS);
                    set.add(RelationType.IMPLEMENTS);
                }
                case FIELD_ACCESS -> {
                    set.add(RelationType.READS_FIELD);
                    set.add(RelationType.WRITES_FIELD);
                }
                case TYPE_DEPENDS -> set.add(RelationType.DEPENDS_ON);
                case IMPORTS -> set.add(RelationType.IMPORTS);
                case STRUCTURE -> {
                    set.add(RelationType.CONTAINS_MODULE);
                    set.add(RelationType.CONTAINS_PACKAGE);
                    set.add(RelationType.CONTAINS_SUB);
                    set.add(RelationType.CONTAINS_FILE);
                    set.add(RelationType.DEFINES_TYPE);
                    set.add(RelationType.HAS_METHOD);
                    set.add(RelationType.HAS_FIELD);
                    set.add(RelationType.INNER_CLASS_OF);
                }
            }
        }
        return set;
    }
}
