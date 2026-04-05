package com.sigma.ai.evaluation.infrastructure.pg.mapper;

import com.sigma.ai.evaluation.infrastructure.pg.po.CommitRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_commit_record 表 MyBatis Mapper。
 */
@Mapper
public interface CommitRecordMapper {

    /**
     * 检查指定仓库的 commit 是否已处理。
     *
     * @param repoId     仓库 ID
     * @param commitHash commit hash
     * @return 记录数（> 0 表示已处理）
     */
    int countByRepoIdAndCommitHash(@Param("repoId") String repoId,
                                   @Param("commitHash") String commitHash);

    /**
     * 插入已处理记录。
     *
     * @param po 提交记录 PO
     */
    void insert(CommitRecordPO po);
}
