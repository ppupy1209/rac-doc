package com.yeonwoo.askwiki.document;

import com.yeonwoo.askwiki.common.CreateDocumentRequest;
import com.yeonwoo.askwiki.embedding.EmbeddingClient;
import com.yeonwoo.askwiki.search.InMemoryVectorIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class GhostIndexTest {

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
    Chunker chunker;

    @Autowired
    InMemoryVectorIndex vectorIndex;

    @MockBean
    EmbeddingClient embeddingClient;

    @BeforeEach
    void rebuildVectorIndex() {
        vectorIndex.rebuild();
    }

    @Test
    void startsWithEmptyVectorIndexAfterRebuild() {
        assertEquals(0, vectorIndex.size());
    }

    @Test
    void leavesNoVectorIndexEntriesAfterRolledBackCreate() {
        String content = threeChunkContent();
        assertEquals(3, chunker.split(content).size());

        when(embeddingClient.embed(anyString()))
                .thenReturn(new float[]{1.0f, 0.0f})
                .thenReturn(new float[]{0.0f, 1.0f})
                .thenThrow(new RuntimeException("third chunk embedding failed"));

        assertThrows(RuntimeException.class, () -> documentService.create(
                new CreateDocumentRequest("rollback reproduction", content)
        ));

        assertEquals(0, documentRepository.count());
        assertEquals(0, chunkRepository.count());

        int ghostEntryCount = vectorIndex.size();
        assertEquals(0, ghostEntryCount,
                "Expected vectorIndex.size() to be 0 after rolled-back create, but found "
                        + ghostEntryCount + " ghost entries");
    }

    private String threeChunkContent() {
        return IntStream.range(0, 119)
                .mapToObj(i -> String.format("word%06d", i))
                .collect(Collectors.joining(" "));
    }
}
