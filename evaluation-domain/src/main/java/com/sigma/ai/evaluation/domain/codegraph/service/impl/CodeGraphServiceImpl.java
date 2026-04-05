package com.sigma.ai.evaluation.domain.codegraph.service.impl;

import com.google.common.collect.Lists;
import com.sigma.ai.evaluation.domain.codegraph.adapter.GraphAdapter;
import com.sigma.ai.evaluation.domain.codegraph.model.*;
import com.sigma.ai.evaluation.domain.codegraph.service.CodeGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码图谱写入服务实现，委托 GraphAdapter 执行 Neo4j 批量写入。
 *
 * <p>节点写入顺序遵循层次依赖：先父后子，确保关系 MERGE 时两端节点均已存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGraphServiceImpl implements CodeGraphService {

    private static final int RELATION_BATCH_SIZE = 500;

    private final GraphAdapter graphAdapter;

    @Override
    public void writeRepositoryNode(RepositoryNode node) {
        graphAdapter.batchMergeRepositoryNodes(List.of(node));
        log.debug("仓库节点写入完成: repoId={}", node.getId());
    }

    @Override
    public void batchWriteParseResults(List<ParseResult> results) {
        if (results == null || results.isEmpty()) return;

        List<PackageNode> packages = new ArrayList<>();
        List<JavaFileNode> fileNodes = new ArrayList<>();
        List<TypeNode> typeNodes = new ArrayList<>();
        List<MethodNode> methodNodes = new ArrayList<>();
        List<FieldNode> fieldNodes = new ArrayList<>();
        List<GraphRelation> relations = new ArrayList<>();

        for (ParseResult r : results) {
            if (r.getPackageNode() != null) packages.add(r.getPackageNode());
            if (r.getJavaFileNode() != null) fileNodes.add(r.getJavaFileNode());
            typeNodes.addAll(r.getTypes());
            methodNodes.addAll(r.getMethods());
            fieldNodes.addAll(r.getFields());
            relations.addAll(r.getRelations());
        }

        // 节点顺序：先父后子，保证 MERGE 时节点已存在
        if (!packages.isEmpty()) graphAdapter.batchMergePackageNodes(packages);
        if (!fileNodes.isEmpty()) graphAdapter.batchMergeJavaFileNodes(fileNodes);
        if (!typeNodes.isEmpty()) graphAdapter.batchMergeTypeNodes(typeNodes);
        if (!methodNodes.isEmpty()) graphAdapter.batchMergeMethodNodes(methodNodes);
        if (!fieldNodes.isEmpty()) graphAdapter.batchMergeFieldNodes(fieldNodes);

        // 关系分批写入，避免单批过大
        Lists.partition(relations, RELATION_BATCH_SIZE).forEach(graphAdapter::batchMergeRelations);

        log.debug("批量写入完成: packages={}, files={}, types={}, methods={}, fields={}, relations={}",
                packages.size(), fileNodes.size(), typeNodes.size(),
                methodNodes.size(), fieldNodes.size(), relations.size());
    }

    @Override
    public String getFileChecksum(String filePath) {
        return graphAdapter.getFileChecksum(filePath);
    }
}
