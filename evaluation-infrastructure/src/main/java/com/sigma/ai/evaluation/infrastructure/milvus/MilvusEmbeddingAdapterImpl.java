package com.sigma.ai.evaluation.infrastructure.milvus;

import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchHit;
import com.sigma.ai.evaluation.domain.embedding.model.EmbeddingSearchQuery;
import com.sigma.ai.evaluation.infrastructure.embedding.EmbeddingApiClient;
import com.sigma.ai.evaluation.types.ErrorCode;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EmbeddingStoreAdapter} 的 Milvus 实现。
 *
 * <p>向量集合名称：{@value #COLLECTION_NAME}，字段布局参见设计文档第五节。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusEmbeddingAdapterImpl implements EmbeddingStoreAdapter {

    private static final String COLLECTION_NAME = "code_node_embedding";
    private static final String FIELD_NODE_ID = "node_id";
    private static final String FIELD_NODE_TYPE = "node_type";
    private static final String FIELD_QUALIFIED_NAME = "qualified_name";
    private static final String FIELD_REPO_ID = "repo_id";
    private static final String FIELD_EMBEDDING = "embedding";

    private final MilvusServiceClient milvusClient;
    private final EmbeddingApiClient embeddingApiClient;

    @Override
    public void upsertEmbedding(String nodeId, String nodeType, String qualifiedName,
                                 String repoId, String text) {
        float[] vector = embeddingApiClient.embed(text);
        if (vector == null) {
            log.warn("Embedding 向量获取失败，跳过写入: nodeId={}", nodeId);
            return;
        }
        doInsert(List.of(nodeId), List.of(nodeType), List.of(qualifiedName),
                repoId, List.of(vector));
    }

    @Override
    public void batchUpsertEmbeddings(List<String> nodeIds, List<String> nodeTypes,
                                       List<String> qualifiedNames, String repoId,
                                       List<String> texts) {
        if (nodeIds.isEmpty()) return;
        List<float[]> vectors = embeddingApiClient.batchEmbed(texts);
        if (vectors == null || vectors.size() != nodeIds.size()) {
            log.warn("Embedding 批量向量数量不匹配，跳过写入: expected={}, got={}",
                    nodeIds.size(), vectors == null ? 0 : vectors.size());
            return;
        }
        doInsert(nodeIds, nodeTypes, qualifiedNames, repoId, vectors);
    }

    @Override
    public void deleteEmbedding(String nodeId) {
        try {
            R<io.milvus.grpc.MutationResult> result = deleteByNodeIds(List.of(nodeId));
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 删除向量失败: nodeId={}, status={}", nodeId, result.getStatus());
            }
        } catch (Exception e) {
            log.error("[{}] Milvus 删除向量异常: nodeId={}", ErrorCode.MILVUS_DELETE_ERROR.getCode(), nodeId, e);
        }
    }

    @Override
    public List<String> semanticSearch(String queryText, String repoId, int topK) {
        List<EmbeddingSearchHit> rich = semanticSearchRich(EmbeddingSearchQuery.builder()
                .queryText(queryText)
                .repoId(repoId)
                .topK(topK)
                .minScore(Float.NEGATIVE_INFINITY)
                .build());
        return rich.stream().map(EmbeddingSearchHit::getNodeId).toList();
    }

    @Override
    public List<EmbeddingSearchHit> semanticSearchRich(EmbeddingSearchQuery query) {
        if (query == null || query.getQueryText() == null || query.getQueryText().isBlank()) {
            return Collections.emptyList();
        }
        float[] queryVector = embeddingApiClient.embed(query.getQueryText());
        if (queryVector == null) {
            log.warn("语义检索 Embedding 失败，返回空结果");
            return Collections.emptyList();
        }

        List<Float> queryList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            queryList.add(v);
        }

        int topK = Math.max(1, query.getTopK());
        // 后置 minScore 过滤时多取一些候选，避免截断后不足 topK
        int requestK = Math.min(512, Math.max(topK * 5, topK));

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withVectorFieldName(FIELD_EMBEDDING)
                .withVectors(List.of(queryList))
                .withTopK(requestK)
                .withMetricType(io.milvus.param.MetricType.IP)
                .withParams("{\"ef\":64}")
                .withOutFields(List.of(FIELD_NODE_ID, FIELD_NODE_TYPE, FIELD_QUALIFIED_NAME));

        String expr = buildSearchExpr(query.getRepoId(), query.getNodeTypes());
        if (expr != null && !expr.isBlank()) {
            builder.withExpr(expr);
        }

        try {
            R<SearchResults> result = milvusClient.search(builder.build());
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 搜索失败: status={}", result.getStatus());
                return Collections.emptyList();
            }
            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            List<EmbeddingSearchHit> hits = new ArrayList<>();
            for (var idScore : wrapper.getIDScore(0)) {
                float score = idScore.getScore();
                if (score < query.getMinScore()) {
                    continue;
                }
                String nodeId = idScore.getStrID();
                if (nodeId == null || nodeId.isBlank()) {
                    continue;
                }
                var fv = idScore.getFieldValues();
                String nodeType = fv == null ? "" : stringOrEmpty(fv.get(FIELD_NODE_TYPE));
                String qn = fv == null ? "" : stringOrEmpty(fv.get(FIELD_QUALIFIED_NAME));
                hits.add(EmbeddingSearchHit.builder()
                        .nodeId(nodeId)
                        .score(score)
                        .nodeType(nodeType)
                        .qualifiedName(qn.isEmpty() ? nodeId : qn)
                        .evidenceSnippet(null)
                        .build());
                if (hits.size() >= topK) {
                    break;
                }
            }
            return hits;
        } catch (Exception e) {
            log.error("[{}] Milvus 语义检索异常", ErrorCode.MILVUS_SEARCH_ERROR.getCode(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteEmbeddingsByNodeIds(List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        List<String> distinct = nodeIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        final int batch = 100;
        for (int i = 0; i < distinct.size(); i += batch) {
            List<String> part = distinct.subList(i, Math.min(i + batch, distinct.size()));
            try {
                R<io.milvus.grpc.MutationResult> result = deleteByNodeIds(part);
                if (result.getStatus() != R.Status.Success.getCode()) {
                    log.warn("Milvus 批量删除向量失败: status={}, count={}", result.getStatus(), part.size());
                }
            } catch (Exception e) {
                log.error("[{}] Milvus 批量删除向量异常: count={}", ErrorCode.MILVUS_DELETE_ERROR.getCode(), part.size(), e);
            }
        }
    }

    private String buildSearchExpr(String repoId, List<String> nodeTypes) {
        List<String> parts = new ArrayList<>();
        if (repoId != null && !repoId.isBlank()) {
            parts.add(FIELD_REPO_ID + " == " + quoteMilvusString(repoId));
        }
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            String inList = nodeTypes.stream()
                    .filter(nt -> nt != null && !nt.isBlank())
                    .map(this::quoteMilvusString)
                    .collect(Collectors.joining(", "));
            if (!inList.isEmpty()) {
                parts.add(FIELD_NODE_TYPE + " in [" + inList + "]");
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" && ", parts);
    }

    private static String stringOrEmpty(Object v) {
        return v == null ? "" : v.toString();
    }

    private void doInsert(List<String> nodeIds, List<String> nodeTypes,
                           List<String> qualifiedNames, String repoId,
                           List<float[]> vectors) {
        try {
            R<io.milvus.grpc.MutationResult> deleteResult = deleteByNodeIds(nodeIds);
            if (deleteResult.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 预删除旧向量失败，继续尝试写入: status={}, nodeCount={}",
                        deleteResult.getStatus(), nodeIds.size());
            }

            List<List<Float>> embeddingList = vectors.stream()
                    .map(arr -> {
                        List<Float> list = new ArrayList<>(arr.length);
                        for (float v : arr) list.add(v);
                        return list;
                    })
                    .toList();

            List<String> repoIds = Collections.nCopies(nodeIds.size(), repoId);

            List<InsertParam.Field> fields = List.of(
                    new InsertParam.Field(FIELD_NODE_ID, nodeIds),
                    new InsertParam.Field(FIELD_NODE_TYPE, nodeTypes),
                    new InsertParam.Field(FIELD_QUALIFIED_NAME, qualifiedNames),
                    new InsertParam.Field(FIELD_REPO_ID, repoIds),
                    new InsertParam.Field(FIELD_EMBEDDING, embeddingList)
            );

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            R<io.milvus.grpc.MutationResult> result = milvusClient.insert(insertParam);
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 写入向量失败: status={}, nodeCount={}", result.getStatus(), nodeIds.size());
            } else {
                log.debug("Milvus 向量写入成功: nodeCount={}", nodeIds.size());
            }
        } catch (Exception e) {
            log.error("[{}] Milvus 写入向量异常: nodeCount={}", ErrorCode.MILVUS_WRITE_ERROR.getCode(), nodeIds.size(), e);
        }
    }

    private R<io.milvus.grpc.MutationResult> deleteByNodeIds(List<String> nodeIds) {
        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withExpr(buildInExpr(nodeIds))
                .build();
        return milvusClient.delete(param);
    }

    private String buildInExpr(List<String> nodeIds) {
        return FIELD_NODE_ID + " in [" + nodeIds.stream()
                .map(this::quoteMilvusString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + "]";
    }

    private String quoteMilvusString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
