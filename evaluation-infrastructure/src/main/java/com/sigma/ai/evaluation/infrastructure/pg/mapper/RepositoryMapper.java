package com.sigma.ai.evaluation.infrastructure.pg.mapper;

import com.sigma.ai.evaluation.infrastructure.pg.po.RepositoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * t_repository 表 MyBatis Mapper。
 */
@Mapper
public interface RepositoryMapper {

    /**
     * 查询所有 ACTIVE 状态仓库。
     *
     * @return 仓库 PO 列表
     */
    List<RepositoryPO> selectAllActive();

    /**
     * 按主键查询仓库。
     *
     * @param id 仓库 ID
     * @return RepositoryPO，不存在时返回 null
     */
    RepositoryPO selectById(@Param("id") String id);
}
