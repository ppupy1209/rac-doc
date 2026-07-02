package com.yeonwoo.ragdoc.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yeonwoo.ragdoc.common.RagResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Component
public class QueryCache {

    private final Cache<String, RagResult.Answered> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public Optional<RagResult.Answered> get(String question) {
        return Optional.ofNullable(cache.getIfPresent(normalize(question)));
    }

    public void put(String question, RagResult.Answered answer) {
        cache.put(normalize(question), answer);
    }

    private String normalize(String question) {
        return question.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
