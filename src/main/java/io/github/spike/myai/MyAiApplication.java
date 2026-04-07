package io.github.spike.myai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 应用启动入口。
 *
 * <p>该类仅负责启动容器，不承载业务逻辑。
 * 业务实现位于各个领域模块（如 ingest）中。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class MyAiApplication {

    /**
     * 应用主函数。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }

}
