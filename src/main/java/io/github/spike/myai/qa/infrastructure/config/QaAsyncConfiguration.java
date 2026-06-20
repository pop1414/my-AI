package io.github.spike.myai.qa.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * QA 子域异步执行器配置。
 *
 * <p>为 {@link io.github.spike.myai.qa.infrastructure.retrieval.HybridChunkRetrievalAdapter}
 * 提供虚拟线程执行器，使 Dense/Sparse 两路阻塞 JDBC 调用在虚拟线程上并行执行，
 * 避免 ForkJoinPool.commonPool() 线程饥饿。
 *
 * @author spike
 * @since 1.0.0
 */
@Configuration
public class QaAsyncConfiguration {

    /**
     * 注册虚拟线程执行器。
     *
     * <p>Java 21 虚拟线程适用于阻塞 IO 场景（JDBC），每个任务独立一个虚拟线程，
     * 不占用平台线程，不阻塞 ForkJoinPool。
     *
     * @return 虚拟线程 per-task 执行器
     */
    @Bean
    @Primary
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
