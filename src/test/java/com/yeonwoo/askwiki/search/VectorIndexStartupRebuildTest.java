package com.yeonwoo.askwiki.search;

import com.yeonwoo.askwiki.embedding.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "askwiki.outbox.scheduler-enabled=false",
        "askwiki.vector-index.impl=memory"
})
@Testcontainers
class VectorIndexStartupRebuildTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Autowired
    ConfigurableApplicationContext context;

    @SpyBean
    InMemoryVectorIndex vectorIndex;

    @MockBean
    EmbeddingClient embeddingClient;

    /**
     * {@link com.yeonwoo.askwiki.config.VectorIndexConfig} re-exposes the active index under a second
     * bean name, and Spring registers {@code @EventListener} methods per bean name. While the listener
     * lived on the index itself, one startup loaded every chunk from MySQL twice.
     */
    @Test
    void rebuildsTheActiveIndexOncePerApplicationReadyEvent() {
        clearInvocations(vectorIndex);

        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[0], context, Duration.ZERO));

        verify(vectorIndex, times(1)).rebuild();
    }
}
