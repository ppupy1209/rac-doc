package com.yeonwoo.ragdoc.rag;

import com.yeonwoo.ragdoc.common.RagResult;
import com.yeonwoo.ragdoc.embedding.EmbeddingClient;
import com.yeonwoo.ragdoc.search.SearchService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class RagService {

    private final EmbeddingClient embeddingClient;
    private final SearchService searchService;
    private final ChatModel chatModel;

    public RagService(EmbeddingClient embeddingClient, SearchService searchService, ChatModel chatModel) {
        this.embeddingClient = embeddingClient;
        this.searchService = searchService;
        this.chatModel = chatModel;
    }

    public RagResult answer(String question, int topK) {
        // TODO(연우): docs/LEARNING-rag.md 참고 — 5단계
        //  1. embeddingClient.embed(question)
        //  2. searchService.findSimilar(vector, topK)  (비면 NoContext)
        //  3. 텍스트 블록 프롬프트에 컨텍스트+질문 조립 ("컨텍스트 밖이면 모른다고 답하라")
        //  4. chatModel.call(prompt)
        //  5. Answered(answer, sources) 반환 / 예외는 LlmError
        return new RagResult.NoContext();
    }
}
