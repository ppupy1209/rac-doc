package com.yeonwoo.askwiki.rag;

import com.yeonwoo.askwiki.common.ChunkMatch;
import com.yeonwoo.askwiki.common.RagResult;
import com.yeonwoo.askwiki.common.Source;
import com.yeonwoo.askwiki.embedding.EmbeddingClient;
import com.yeonwoo.askwiki.search.VectorIndex;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgenticRagService {

    private static final String AGENTIC_SYSTEM_PROMPT = """
            당신은 사내 규정 안내 도우미입니다. 답에 필요한 정보는 반드시 search_wiki 도구로 사내 위키에서 찾습니다.
            - 한 번의 검색으로 부족하면 질의를 바꿔 여러 번 검색합니다.
            - 검색으로 찾은 내용만 근거로 한국어로 간결하게(1~3문장) 답합니다.
            - 근로기준법·일반 상식·외부 지식으로 답하지 않습니다. 검색해도 근거가 없으면 "모르겠습니다"라고만 답합니다.
            """;

    private final ChatModel chatModel;
    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    public AgenticRagService(ChatModel chatModel, EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        this.chatModel = chatModel;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    public RagResult answer(String question, int topK) {
        try {
            WikiSearchTool tool = new WikiSearchTool(embeddingClient, vectorIndex, topK);
            ChatResponse response = ChatClient.create(chatModel)
                    .prompt()
                    .system(AGENTIC_SYSTEM_PROMPT)
                    .user(question)
                    .tools(tool)
                    .call()
                    .chatResponse();

            String answer = response.getResult().getOutput().getText();
            return mapResult(answer, tool.retrieved());
        } catch (Exception e) {
            return new RagResult.LlmError(e.getMessage());
        }
    }

    static RagResult mapResult(String answer, List<ChunkMatch> retrieved) {
        if (retrieved.isEmpty() || answer.contains("모르겠습니다")) {
            return new RagResult.NoContext();
        }

        Map<Long, ChunkMatch> uniqueChunks = new LinkedHashMap<>();
        retrieved.forEach(match -> uniqueChunks.putIfAbsent(match.chunkId(), match));

        List<Source> sources = uniqueChunks.values().stream()
                .map(match -> new Source(match.documentId(), match.title(), match.seq(), match.score()))
                .toList();

        return new RagResult.Answered(answer, sources);
    }
}
