package com.yeonwoo.askwiki.eval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeonwoo.askwiki.common.ChunkMatch;
import com.yeonwoo.askwiki.common.CreateDocumentRequest;
import com.yeonwoo.askwiki.document.ChunkRepository;
import com.yeonwoo.askwiki.document.DocumentRepository;
import com.yeonwoo.askwiki.document.DocumentService;
import com.yeonwoo.askwiki.embedding.EmbeddingClient;
import com.yeonwoo.askwiki.embedding.EmbeddingCodec;
import com.yeonwoo.askwiki.search.EsVectorIndex;
import com.yeonwoo.askwiki.search.IndexOutboxRelay;
import com.yeonwoo.askwiki.search.IndexOutboxRepository;
import com.yeonwoo.askwiki.search.VectorIndex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B5-3 측정: BM25+kNN 하이브리드가 벡터 단독보다 검색을 실제로 개선하는가.
 *
 * <p><b>왜 3팔인가 (측정 설계의 핵심)</b>: 하이브리드는 융합 재료를 넓히려 kNN 결과를 더 많이 가져오고, 그 부작용으로
 * num_candidates가 커진다(벡터 단독 100 vs 하이브리드 500). nc는 kNN 재현율을 좌우하므로(C1 실측 dial:
 * nc 100 → 0.83, nc 200 → 0.879) 2팔로 재면 <b>"하이브리드가 좋다"와 "후보를 더 봤다"가 뒤섞인다</b>.
 * 그래서 nc만 올린 통제군(B)을 끼워 넣는다:
 * <ul>
 *   <li>A = 벡터 단독, nc 100 — 현 프로덕션 기준선</li>
 *   <li>B = 벡터 단독, nc 500 — A→B 차이 = <b>후보 증가 효과</b></li>
 *   <li>C = 하이브리드, nc 500 — B→C 차이 = <b>BM25 융합의 순수 기여</b></li>
 * </ul>
 * <b>A == B는 이 코퍼스에서 교란 변수가 실제로 0임을 보이는 통제다</b>(8문서=8청크라 nc 100도 전수 탐색).
 * 그게 성립하면 A→C 차이 전부를 BM25에 귀속할 수 있다.
 *
 * <p>실제 Ollama(nomic-embed-text) 임베딩이 필요하다. 챗 LLM은 쓰지 않으므로 결정적이다.
 * 실행: {@code ./gradlew evalTest --tests "*HybridSearchMatrixEvalTest"}.
 */
@Tag("eval")
@SpringBootTest
@Testcontainers
class HybridSearchMatrixEvalTest {

    private static final List<Integer> TOP_KS = List.of(1, 2, 4, 8);
    private static final int SEARCH_DEPTH = 8;

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.17.4")
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("askwiki.es.url", elasticsearch::getHttpHostAddress);
        registry.add("askwiki.vector-index.impl", () -> "elasticsearch");
        registry.add("askwiki.es.refresh-on-write", () -> "true");
        registry.add("askwiki.outbox.scheduler-enabled", () -> "false");
    }

    @Autowired DocumentService documentService;
    @Autowired VectorIndex vectorIndex;
    @Autowired EmbeddingClient embeddingClient;
    @Autowired EmbeddingCodec embeddingCodec;
    @Autowired IndexOutboxRelay relay;
    @Autowired DocumentRepository documentRepository;
    @Autowired ChunkRepository chunkRepository;
    @Autowired IndexOutboxRepository outboxRepository;
    @Autowired ElasticsearchClient elasticsearchClient;

    @Value("${askwiki.es.index:askwiki-chunks}")
    String indexName;

    /** 팔 하나: 이름 + 그 팔의 검색기. 셋 다 <b>같은 인덱스</b>를 읽는다 — 다른 것은 질의 전략뿐이다. */
    private record Arm(String label, VectorIndex index) {}

    @Test
    void comparesVectorOnlyVsHybrid() throws IOException {
        outboxRepository.deleteAll();
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        vectorIndex.rebuild();

        Map<String, Long> slugToId = loadCorpus();
        relay.processPendingEvents();

        List<Arm> arms = List.of(
                new Arm("A 벡터 단독 nc=100 (기준선)", arm(false, 0)),
                new Arm("B 벡터 단독 nc=500 (통제)", arm(false, 500)),
                new Arm("C 하이브리드  nc=500", arm(true, 500))
        );

        JsonNode answerable;
        try (InputStream inputStream = new ClassPathResource("eval/questions.json").getInputStream()) {
            answerable = new ObjectMapper().readTree(inputStream).get("answerable");
        }

        Map<String, Map<Integer, Integer>> hitsByArm = new LinkedHashMap<>();
        arms.forEach(a -> {
            Map<Integer, Integer> hits = new LinkedHashMap<>();
            TOP_KS.forEach(k -> hits.put(k, 0));
            hitsByArm.put(a.label(), hits);
        });

        int total = 0;
        for (JsonNode node : answerable) {
            total++;
            String question = node.get("question").asText();
            Long expectedDocId = slugToId.get(node.get("expectedDocSlug").asText());
            // 임베딩은 한 번만 — 팔 사이에서 임베딩이 변수가 되지 않게 하는 통제(C2의 '임베딩 고정'과 같은 규칙).
            float[] questionVector = embeddingClient.embed(question);

            for (Arm a : arms) {
                List<Long> topDocIds = a.index().search(question, questionVector, SEARCH_DEPTH).stream()
                        .map(ChunkMatch::documentId)
                        .toList();
                for (int k : TOP_KS) {
                    if (containsDocumentAtK(topDocIds, expectedDocId, k)) {
                        hitsByArm.get(a.label()).merge(k, 1, Integer::sum);
                    }
                }
            }
        }

        for (Arm a : arms) {
            Map<Integer, Integer> hits = hitsByArm.get(a.label());
            System.out.println(String.format(
                    "[HYBRID-MATRIX] %-28s | @1=%5.1f%% @2=%5.1f%% @4=%5.1f%% @8=%5.1f%%",
                    a.label(),
                    rate(hits.get(1), total), rate(hits.get(2), total),
                    rate(hits.get(4), total), rate(hits.get(8), total)
            ));
        }
        reportMotivatingCase(arms, slugToId);

        assertEquals(30, total);
        // 통제: 8문서=8청크라 nc 100도 전수 탐색 → A와 B는 반드시 같아야 한다.
        // 다르면 이 코퍼스에서도 후보 수가 결과를 흔든다는 뜻이고, 그러면 A→C를 BM25에 귀속할 수 없다.
        assertEquals(hitsByArm.get(arms.get(0).label()), hitsByArm.get(arms.get(1).label()),
                "교란 변수 통제 실패: nc만 바꿨는데 결과가 달라졌다 → A→C 차이를 BM25에 귀속할 수 없다");
        for (Arm a : arms) {
            Map<Integer, Integer> h = hitsByArm.get(a.label());
            assertTrue(h.get(1) <= h.get(2) && h.get(2) <= h.get(4) && h.get(4) <= h.get(8));
        }
    }

    /** B5를 승격시킨 그 케이스: 재작성이 만든 흠 없는 질의인데 벡터는 vacation을 5위/8에 뒀다. */
    private void reportMotivatingCase(List<Arm> arms, Map<String, Long> slugToId) {
        String question = "연차 휴가는 언제까지 사용해야 하나요?";
        Long expected = slugToId.get("vacation");
        float[] questionVector = embeddingClient.embed(question);

        for (Arm a : arms) {
            List<Long> topDocIds = a.index().search(question, questionVector, SEARCH_DEPTH).stream()
                    .map(ChunkMatch::documentId)
                    .toList();
            int rank = topDocIds.indexOf(expected) + 1;
            System.out.println(String.format(
                    "[HYBRID-CASE] %-28s | \"%s\" → vacation %s",
                    a.label(), question, rank > 0 ? rank + "위" : "8위 밖"
            ));
        }
    }

    private VectorIndex arm(boolean hybrid, long numCandidates) {
        return new EsVectorIndex(elasticsearchClient, embeddingCodec, chunkRepository, documentRepository,
                indexName, true, hybrid, "elasticsearch", numCandidates);
    }

    private Map<String, Long> loadCorpus() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:eval/corpus/*.md");
        Map<String, Long> slugToId = new HashMap<>();

        for (Resource resource : resources) {
            String slug = Objects.requireNonNull(resource.getFilename()).replaceFirst("\\.md$", "");
            String text;
            try (InputStream inputStream = resource.getInputStream()) {
                text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            String[] parts = text.split("\\R", 2);
            String title = parts[0].replaceFirst("^#\\s*", "").trim();
            String content = parts.length > 1 ? parts[1].trim() : "";
            if (content.isBlank()) {
                content = text;
            }

            slugToId.put(slug, documentService.create(new CreateDocumentRequest(title, content)).id());
        }

        return slugToId;
    }

    private boolean containsDocumentAtK(List<Long> topDocIds, Long expectedDocId, int k) {
        if (expectedDocId == null) {
            return false;
        }
        return topDocIds.stream().limit(k).anyMatch(expectedDocId::equals);
    }

    private double rate(int hits, int total) {
        return total == 0 ? 0.0 : hits * 100.0 / total;
    }
}
