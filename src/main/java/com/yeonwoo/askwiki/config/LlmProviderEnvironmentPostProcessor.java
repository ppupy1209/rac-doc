package com.yeonwoo.askwiki.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code askwiki.llm.provider}(ollama|gemini) 값에 따라 Spring AI가 어느 챗 모델을
 * 오토컨픽할지({@code spring.ai.model.chat})를 결정한다.
 *
 * <p>선택되지 않은 프로바이더의 ChatModel 오토컨픽을 꺼서 두 가지를 동시에 해결한다:
 * (1) 두 스타터(Ollama·OpenAI)가 각각 ChatModel 빈을 만들어 생기는 모호성 → {@code @Primary} 불필요,
 * (2) 미선택 프로바이더의 키/설정이 부팅을 막지 않음(예: provider=ollama일 때 Gemini 키 없이도 부팅).
 *
 * <p>gemini는 OpenAI 호환 엔드포인트를 사용하므로 openai 챗 모델로 매핑한다
 * (application.yml의 {@code spring.ai.openai.*}가 Gemini를 가리킴). 임베딩은
 * application.yml의 {@code spring.ai.model.embedding=ollama}로 항상 Ollama(nomic-embed-text) 고정.
 */
public final class LlmProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROVIDER_PROPERTY = "askwiki.llm.provider";
    static final String PROVIDER_ENV = "ASKWIKI_LLM_PROVIDER";
    static final String CHAT_MODEL_PROPERTY = "spring.ai.model.chat";
    static final String DEFAULT_PROVIDER = "ollama";

    private static final Map<String, String> CHAT_MODEL_BY_PROVIDER = Map.of(
            "ollama", "ollama",
            "gemini", "openai"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = resolveProvider(environment);
        String chatModel = CHAT_MODEL_BY_PROVIDER.getOrDefault(provider, DEFAULT_PROVIDER);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put(CHAT_MODEL_PROPERTY, chatModel);
        // 파생 값이므로 최우선으로 주입해 다른 소스가 덮어쓰지 못하게 한다.
        environment.getPropertySources()
                .addFirst(new MapPropertySource("askwikiLlmProvider", overrides));
    }

    private String resolveProvider(ConfigurableEnvironment environment) {
        String provider = environment.getProperty(PROVIDER_PROPERTY);
        if (provider == null || provider.isBlank()) {
            provider = environment.getProperty(PROVIDER_ENV, DEFAULT_PROVIDER);
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public int getOrder() {
        // config data(application.yml) 로드 후 실행되도록 가장 낮은 우선순위 →
        // yml에 정의된 askwiki.llm.provider 값을 읽을 수 있다.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
