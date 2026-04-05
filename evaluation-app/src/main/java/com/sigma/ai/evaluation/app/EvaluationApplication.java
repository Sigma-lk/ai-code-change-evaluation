package com.sigma.ai.evaluation.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类。
 *
 * <p>包扫描覆盖 com.sigma.ai.evaluation 下所有子模块的 Bean；
 * MapperScan 指向 infrastructure 模块的 MyBatis Mapper 接口包。
 */
@SpringBootApplication(scanBasePackages = "com.sigma.ai.evaluation")
@MapperScan("com.sigma.ai.evaluation.infrastructure.pg.mapper")
@EnableScheduling
@EnableConfigurationProperties
public class EvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluationApplication.class, args);
    }
}
