package com.yeonwoo.askwiki.search;

import com.yeonwoo.askwiki.common.CreateDocumentRequest;
import com.yeonwoo.askwiki.document.ChunkRepository;
import com.yeonwoo.askwiki.document.Chunker;
import com.yeonwoo.askwiki.document.DocumentRepository;
import com.yeonwoo.askwiki.document.DocumentService;
import com.yeonwoo.askwiki.embedding.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "askwiki.outbox.scheduler-enabled=false")
@Testcontainers
class IndexOutboxRelayFailureTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    ChunkRepository chunkRepository;

    @Autowired
    IndexOutboxRepository outboxRepository;

    @Autowired
    IndexOutboxRelay relay;

    @Autowired
    Chunker chunker;

    @MockBean
    EmbeddingClient embeddingClient;

    @SpyBean
    InMemoryVectorIndex vectorIndex;

    @BeforeEach
    void rebuildVectorIndex() {
        outboxRepository.deleteAll();
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        vectorIndex.rebuild();
    }

    @Test
    void recoversWithoutLossWhenRelayCrashesMidBatch() {
        createThreeChunkDocument();

        assertEquals(3, outboxRepository.count());
        assertEquals(0, vectorIndex.size());

        AtomicInteger addCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (addCalls.incrementAndGet() == 2) {
                throw new RuntimeException("relay crashed before marking");
            }
            return invocation.callRealMethod();
        }).when(vectorIndex).add(any());

        assertThrows(RuntimeException.class, () -> relay.processPendingEvents());

        assertEquals(3, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PENDING
        ).size());
        assertEquals(0, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PROCESSED
        ).size());

        int processed = relay.processPendingEvents();

        assertEquals(3, processed);
        // In a real JVM restart, rebuild would restore the index. This test verifies outbox
        // zero-loss and idempotency guarantees when recovering without process restart.
        assertEquals(3, vectorIndex.size());
        assertEquals(0, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PENDING
        ).size());
        assertEquals(3, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PROCESSED
        ).size());
    }

    @Test
    void marksProcessedWhenChunkMissing() {
        createThreeChunkDocument();

        assertEquals(3, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PENDING
        ).size());

        chunkRepository.deleteAll();

        int processed = relay.processPendingEvents();

        assertEquals(3, processed);
        assertEquals(0, vectorIndex.size());
        assertEquals(3, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PROCESSED
        ).size());
        assertEquals(0, outboxRepository.findByStatusOrderByIdAsc(
                IndexOutboxEvent.Status.PENDING
        ).size());
    }

    private void createThreeChunkDocument() {
        String content = threeChunkContent();
        assertEquals(3, chunker.split(content).size());

        when(embeddingClient.embed(anyString()))
                .thenReturn(new float[]{1.0f, 0.0f})
                .thenReturn(new float[]{0.0f, 1.0f})
                .thenReturn(new float[]{1.0f, 1.0f});

        documentService.create(new CreateDocumentRequest("crash recovery", content));
    }

    private String threeChunkContent() {
        return IntStream.range(0, 119)
                .mapToObj(i -> String.format("word%06d", i))
                .collect(Collectors.joining(" "));
    }
}
