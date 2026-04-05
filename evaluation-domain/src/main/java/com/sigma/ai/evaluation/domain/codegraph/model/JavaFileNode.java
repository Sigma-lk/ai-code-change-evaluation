package com.sigma.ai.evaluation.domain.codegraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Neo4j JavaFile 节点，表示一个 Java 源文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JavaFileNode {

    /** 文件绝对路径（唯一键） */
    private String path;

    /** 相对于仓库根目录的路径 */
    private String relativePath;

    /** 文件内容 MD5 checksum，用于全量索引幂等检测 */
    private String checksum;

    /** 文件行数 */
    private int lineCount;

    /** 文件最后修改时间戳（epoch millis） */
    private long lastModified;

    /** 所属仓库 ID */
    private String repoId;
}
