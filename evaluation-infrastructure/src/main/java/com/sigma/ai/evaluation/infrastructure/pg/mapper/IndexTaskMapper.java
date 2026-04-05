package com.sigma.ai.evaluation.infrastructure.pg.mapper;

import com.sigma.ai.evaluation.infrastructure.pg.po.IndexTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_index_task 表 MyBatis Mapper。
 */
@Mapper
public interface IndexTaskMapper {

    /**
     * 插入新任务，插入后 PO 的 id 字段会被回填。
     *
     * @param po 任务 PO
     */
    void insert(IndexTaskPO po);

    /**
     * 更新任务状态、完成时间及错误信息。
     *
     * @param id       任务 ID
     * @param status   新状态
     * @param errorMsg 失败原因（成功时为 null）
     */
    void updateStatus(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("errorMsg") String errorMsg);

    /**
     * 按主键查询任务。
     *
     * @param id 任务 ID
     * @return IndexTaskPO，不存在时返回 null
     */
    IndexTaskPO selectById(@Param("id") Long id);
}
