package com.yeonwoo.askwiki.rag;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C2-3 지표 기록 로직 검증. 실제 LLM/Ollama 없이 SimpleMeterRegistry + 목 ChatResponse로 결정적으로 확인.
 */
class LlmMetricsTest {

    @Test
    void recordsTokensCallsAndLatencyTaggedByProvider() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmMetrics metrics = new LlmMetrics(registry, "gemini");

        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(21);
        when(usage.getCompletionTokens()).thenReturn(13);
        when(metadata.getUsage()).thenReturn(usage);
        when(response.getMetadata()).thenReturn(metadata);

        metrics.record(response, 1_500_000_000L); // 1.5s

        assertThat(registry.counter("llm.calls", "provider", "gemini").count()).isEqualTo(1.0);
        assertThat(registry.counter("llm.tokens", "provider", "gemini", "type", "input").count()).isEqualTo(21.0);
        assertThat(registry.counter("llm.tokens", "provider", "gemini", "type", "output").count()).isEqualTo(13.0);
        assertThat(registry.timer("llm.latency", "provider", "gemini").totalTime(TimeUnit.SECONDS))
                .isCloseTo(1.5, within(0.01));
        // gemini 무료 티어 단가 0 → 비용 0
        assertThat(registry.counter("llm.cost.usd", "provider", "gemini").count()).isEqualTo(0.0);
    }

    @Test
    void toleratesNullUsageButStillCountsCall() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmMetrics metrics = new LlmMetrics(registry, "ollama");

        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(null);
        when(response.getMetadata()).thenReturn(metadata);

        metrics.record(response, 1_000_000L);

        // 호출·지연은 기록되고, usage가 없으면 토큰은 0으로 유지(사전 등록된 카운터).
        assertThat(registry.counter("llm.calls", "provider", "ollama").count()).isEqualTo(1.0);
        assertThat(registry.counter("llm.tokens", "provider", "ollama", "type", "input").count()).isEqualTo(0.0);
        assertThat(registry.counter("llm.tokens", "provider", "ollama", "type", "output").count()).isEqualTo(0.0);
    }
}
