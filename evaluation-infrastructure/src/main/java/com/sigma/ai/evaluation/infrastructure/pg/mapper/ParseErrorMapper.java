package com.sigma.ai.evaluation.infrastructure.pg.mapper;

import com.sigma.ai.evaluation.infrastructure.pg.po.ParseErrorPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_parse_error 表 MyBatis Mapper。
 */
@Mapper
public interface ParseErrorMapper {

    /**
     * 插入解析错误记录。
     *
     * @param po 解析错误 PO
     */
    void insert(ParseErrorPO po);
}
