package com.sigma.ai.evaluation.infrastructure.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置，读取 {@code milvus.host} 与 {@code milvus.port}。
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    /**
     * 创建 MilvusServiceClient Bean，连接到 Milvus Standalone 实例。
     *
     * @return MilvusServiceClient
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("连接 Milvus: {}:{}", host, port);
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        return new MilvusServiceClient(connectParam);
    }
}
