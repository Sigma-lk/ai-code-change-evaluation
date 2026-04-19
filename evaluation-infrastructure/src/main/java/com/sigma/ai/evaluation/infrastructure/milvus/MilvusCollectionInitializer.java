package com.sigma.ai.evaluation.infrastructure.milvus;

import com.sigma.ai.evaluation.infrastructure.embedding.EmbeddingApiProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.R;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Milvus 集合初始化器。
 *
 * <p>应用启动时自动确保 {@code code_node_embedding} 集合、向量索引和加载状态已经就绪。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionInitializer {

    private static final String COLLECTION_NAME = "code_node_embedding";
    private static final String FIELD_NODE_ID = "node_id";
    private static final String FIELD_NODE_TYPE = "node_type";
    private static final String FIELD_QUALIFIED_NAME = "qualified_name";
    private static final String FIELD_REPO_ID = "repo_id";
    private static final String FIELD_EMBEDDING = "embedding";

    private final MilvusServiceClient milvusClient;
    private final EmbeddingApiProperties embeddingApiProperties;

    /**
     * 启动时初始化 Milvus 集合。
     */
    @PostConstruct
    public void initCollection() {
        try {
            boolean exists = hasCollection();
            if (!exists) {
                createCollection();
                createVectorIndex();
                log.info("Milvus 集合初始化完成: collection={}, dimension={}",
                        COLLECTION_NAME, embeddingApiProperties.getDimension());
            } else {
                log.info("Milvus 集合已存在，跳过创建: collection={}", COLLECTION_NAME);
            }
            loadCollection();
        } catch (Exception e) {
            throw new IllegalStateException("Milvus 集合初始化失败: " + COLLECTION_NAME, e);
        }
    }

    private boolean hasCollection() {
        R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("检查 Milvus 集合是否存在失败，status=" + response.getStatus());
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private void createCollection() {
        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("代码节点语义向量集合")
                .withShardsNum(2)
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_NODE_ID)
                        .withDescription("Neo4j 节点 ID")
                        .withDataType(DataType.VarChar)
                        .withPrimaryKey(true)
                        .withMaxLength(1024)
                        .withAutoID(false)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_NODE_TYPE)
                        .withDescription("节点类型")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(64)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_QUALIFIED_NAME)
                        .withDescription("节点全限定名")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(2048)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_REPO_ID)
                        .withDescription("仓库 ID")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(256)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName(FIELD_EMBEDDING)
                        .withDescription("Dense Embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(embeddingApiProperties.getDimension())
                        .build())
                .build();
        R<?> response = milvusClient.createCollection(createCollectionParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("创建 Milvus 集合失败，status=" + response.getStatus());
        }
    }

    private void createVectorIndex() {
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName(FIELD_EMBEDDING)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.IP)
                .withSyncMode(Boolean.TRUE)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build();
        R<?> response = milvusClient.createIndex(createIndexParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("创建 Milvus 向量索引失败，status=" + response.getStatus());
        }
    }

    private void loadCollection() {
        R<?> response = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("加载 Milvus 集合失败，status=" + response.getStatus());
        }
        log.info("Milvus 集合已加载: collection={}", COLLECTION_NAME);
    }
}
