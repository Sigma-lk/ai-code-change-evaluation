package com.sigma.ai.evaluation.infrastructure.milvus;

import com.sigma.ai.evaluation.domain.embedding.adapter.EmbeddingStoreAdapter;
import com.sigma.ai.evaluation.infrastructure.embedding.EmbeddingApiClient;
import com.sigma.ai.evaluation.infrastructure.embedding.EmbeddingApiProperties;
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

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final EmbeddingApiProperties embeddingProps;

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
            DeleteParam param = DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr(FIELD_NODE_ID + " == \"" + nodeId + "\"")
                    .build();
            R<io.milvus.grpc.MutationResult> result = milvusClient.delete(param);
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 删除向量失败: nodeId={}, status={}", nodeId, result.getStatus());
            }
        } catch (Exception e) {
            log.error("Milvus 删除向量异常: nodeId={}", nodeId, e);
        }
    }

    @Override
    public List<String> semanticSearch(String queryText, String repoId, int topK) {
        float[] queryVector = embeddingApiClient.embed(queryText);
        if (queryVector == null) {
            log.warn("语义检索 Embedding 失败，返回空结果");
            return Collections.emptyList();
        }

        List<Float> queryList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) queryList.add(v);

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withVectorFieldName(FIELD_EMBEDDING)
                .withVectors(List.of(queryList))
                .withTopK(topK)
                .withMetricType(io.milvus.param.MetricType.IP)
                .withOutFields(List.of(FIELD_NODE_ID));

        if (repoId != null) {
            builder.withExpr(FIELD_REPO_ID + " == \"" + repoId + "\"");
        }

        try {
            R<SearchResults> result = milvusClient.search(builder.build());
            if (result.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus 搜索失败: status={}", result.getStatus());
                return Collections.emptyList();
            }
            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            return wrapper.getFieldData(FIELD_NODE_ID, 0).stream()
                    .map(Object::toString)
                    .toList();
        } catch (Exception e) {
            log.error("Milvus 语义检索异常", e);
            return Collections.emptyList();
        }
    }

    private void doInsert(List<String> nodeIds, List<String> nodeTypes,
                           List<String> qualifiedNames, String repoId,
                           List<float[]> vectors) {
        try {
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
            log.error("Milvus 写入向量异常: nodeCount={}", nodeIds.size(), e);
        }
    }
}
