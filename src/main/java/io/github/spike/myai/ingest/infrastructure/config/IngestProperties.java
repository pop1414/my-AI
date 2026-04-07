package io.github.spike.myai.ingest.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingest pipeline configuration properties.
 */
@Getter
@ConfigurationProperties(prefix = "myai.ingest")
public class IngestProperties {

    private final Parser parser = new Parser();
    private final Storage storage = new Storage();
    private final Chunk chunk = new Chunk();
    private final Worker worker = new Worker();

    @Setter
    @Getter
    public static class Parser {
        private int maxTextLength = 2000000;
        private boolean parseEmbeddedResource = false;

    }

    @Setter
    @Getter
    public static class Storage {
        private String rootDir = "data/ingest";

    }

    @Setter
    @Getter
    public static class Chunk {
        private int chunkSize = 500;
        private int overlapSize = 100;

    }

    @Setter
    @Getter
    public static class Worker {
        private boolean enabled = false;
        private long pollDelayMs = 5000L;

    }
}
