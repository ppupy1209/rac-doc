package com.yeonwoo.ragdoc.bench;

import com.yeonwoo.ragdoc.document.Chunk;
import com.yeonwoo.ragdoc.document.ChunkRepository;
import com.yeonwoo.ragdoc.document.Document;
import com.yeonwoo.ragdoc.document.DocumentRepository;
import com.yeonwoo.ragdoc.embedding.EmbeddingCodec;
import com.yeonwoo.ragdoc.search.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * study ③ (MySQL 벡터 검색 최적화) 측정 전용.
 *
 * seed:   임의 임베딩(768차원)을 가진 청크를 대량 주입해 N을 키운다 (Ollama 없이 빠르게).
 *         검색 성능(코사인 스캔 비용)만 잴 거라 임베딩 값은 무작위여도 무방하다.
 * search: LLM 없이 SearchService.findSimilar 만 여러 번 돌려 평균 응답시간을 잰다.
 */
@RestController
@RequestMapping("/api/bench")
public class SearchBenchController {

    private static final int DIM = 768; // nomic-embed-text 차원

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingCodec embeddingCodec;
    private final SearchService searchService;

    public SearchBenchController(DocumentRepository documentRepository,
                                 ChunkRepository chunkRepository,
                                 EmbeddingCodec embeddingCodec,
                                 SearchService searchService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingCodec = embeddingCodec;
        this.searchService = searchService;
    }

    /** 임의 임베딩 청크 count개 주입 (500개씩 배치 insert) */
    @PostMapping("/seed")
    public Map<String, Object> seed(@RequestParam(defaultValue = "20000") int count) {
        Document doc = documentRepository.save(new Document("bench-seed", "bench"));
        Random rnd = new Random();
        List<Chunk> batch = new ArrayList<>(500);
        for (int i = 0; i < count; i++) {
            batch.add(new Chunk(doc.getId(), i, "bench chunk " + i,
                    embeddingCodec.serialize(randomVector(rnd)), 1));
            if (batch.size() == 500) {
                chunkRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            chunkRepository.saveAll(batch);
        }
        return Map.of("inserted", count, "totalChunks", chunkRepository.count());
    }

    /** 검색만 runs회 반복해 평균 응답시간(ms) 측정 */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "4") int topK,
                                      @RequestParam(defaultValue = "10") int runs) {
        Random rnd = new Random();
        long totalNanos = 0;
        int lastMatches = 0;
        for (int r = 0; r < runs; r++) {
            float[] query = randomVector(rnd);
            long start = System.nanoTime();
            lastMatches = searchService.findSimilar(query, topK).size();
            totalNanos += System.nanoTime() - start;
        }
        double avgMs = totalNanos / 1_000_000.0 / runs;
        return Map.of(
                "totalChunks", chunkRepository.count(),
                "topK", topK,
                "runs", runs,
                "matches", lastMatches,
                "avgLatencyMs", Math.round(avgMs * 100.0) / 100.0
        );
    }

    private float[] randomVector(Random rnd) {
        float[] v = new float[DIM];
        for (int j = 0; j < DIM; j++) {
            v[j] = rnd.nextFloat();
        }
        return v;
    }
}
