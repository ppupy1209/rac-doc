package com.yeonwoo.askwiki.eval;

import com.yeonwoo.askwiki.document.Chunker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1 Step 3-⑤ (강의 접목 P1-②): 자체 {@link Chunker}(문자 경계·500자) vs Spring AI {@link TokenTextSplitter}(토큰 경계).
 *
 * <p>골든셋 8문서를 양쪽으로 청킹해 문서당 청크 수를 비교한다. 결정적(임베딩 불필요)이라 hit rate까지 안 가도
 * 구조가 같으면(문서당 1청크로 수렴) 임베딩 단위가 동일 → hit rate도 구조상 동일함을 논증할 수 있다.
 * B2-5가 밝힌 "짧은 문서는 청크 크기를 구분 못 한다"의 자매 측정 — 스플리터 선택도 짧은 문서에선 수렴하는가?
 */
@Tag("eval")
class EtlComparisonEvalTest {

    @Test
    void comparesChunkerVsTokenTextSplitter() throws IOException {
        Chunker chunker = new Chunker(500, 50);
        TokenTextSplitter tokenSplitter = new TokenTextSplitter();

        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:eval/corpus/*.md");

        int chunkerTotal = 0;
        int tokenTotal = 0;
        int convergedDocs = 0;

        System.out.println("[ETL-COMPARE] slug | chars | chunker | tokenSplitter");
        for (Resource resource : resources) {
            String slug = Objects.requireNonNull(resource.getFilename()).replaceFirst("\\.md$", "");
            String content = readContent(resource);

            int chunkerCount = chunker.split(content).size();
            int tokenCount = tokenSplitter.split(new Document(content)).size();
            chunkerTotal += chunkerCount;
            tokenTotal += tokenCount;
            if (chunkerCount == tokenCount) {
                convergedDocs++;
            }

            System.out.println(String.format(
                    "[ETL-COMPARE] %-12s | %5d | %d | %d", slug, content.length(), chunkerCount, tokenCount));
        }

        System.out.println(String.format(
                "[ETL-COMPARE] TOTAL docs=%d chunkerChunks=%d tokenChunks=%d convergedDocs=%d",
                resources.length, chunkerTotal, tokenTotal, convergedDocs));

        assertTrue(chunkerTotal >= resources.length);
        assertTrue(tokenTotal >= resources.length);
    }

    /** 이블 러너와 동일하게 H1 제목 줄을 떼고 본문만 청킹 대상으로 삼는다. */
    private String readContent(Resource resource) throws IOException {
        String text;
        try (InputStream inputStream = resource.getInputStream()) {
            text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String[] parts = text.split("\\R", 2);
        String content = parts.length > 1 ? parts[1].strip() : "";
        return content.isBlank() ? text : content;
    }
}
