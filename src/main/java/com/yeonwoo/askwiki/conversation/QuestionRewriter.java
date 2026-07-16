package com.yeonwoo.askwiki.conversation;

import com.yeonwoo.askwiki.rag.LlmCallGuard;
import com.yeonwoo.askwiki.rag.LlmMetrics;
import com.yeonwoo.askwiki.rag.LlmUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuestionRewriter {

    private static final String REWRITE_SYSTEM_PROMPT = """
            당신은 대화형 검색 시스템의 질문 재작성기입니다. 이전 대화를 참고해 마지막 사용자 질문을
            그 자체로 이해되는 독립형 질문으로 다시 쓰세요.

            - 대명사나 생략된 대상을 이전 대화에서 찾아 명시적으로 채웁니다. 예: "그럼 반차는?" → "반차는 며칠까지 사용할 수 있나요?"
            - **채우기만 하고 덧붙이지는 마세요.** 대명사가 실제로 가리키는 대상은 채우되, 사용자가 묻지 않은
              조건·기준·수식어를 이전 답변에서 가져와 추가하지 마세요. 덧붙인 한 마디가 검색을 엉뚱한 문서로 보냅니다.
              예: "그럼 언제까지 써야 하나요?"
                → "연차 휴가는 언제까지 사용해야 하나요?" (O — 생략된 대상만 채움)
                → "연차 휴가는 입사일로부터 언제까지 사용해야 하나요?" (X — '입사일'은 사용자가 묻지 않았다)
            - 이미 독립적으로 이해되는 질문이면 그대로 두세요.
            - 이전 대화와 무관한 새 주제면 원래 질문을 그대로 두세요.
            - 질문에 답하지 말고, 재작성된 질문 한 문장만 출력하세요.
            """;

    private final ChatModel chatModel;
    private final LlmCallGuard llmCallGuard;
    private final LlmMetrics llmMetrics;

    public QuestionRewriter(ChatModel chatModel, LlmCallGuard llmCallGuard, LlmMetrics llmMetrics) {
        this.chatModel = chatModel;
        this.llmCallGuard = llmCallGuard;
        this.llmMetrics = llmMetrics;
    }

    public String rewrite(String question, List<Message> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }

        try {
            long start = System.nanoTime();
            ChatResponse response = llmCallGuard.call(() ->
                    ChatClient.create(chatModel).prompt()
                            .system(REWRITE_SYSTEM_PROMPT).user(buildUserPrompt(question, history))
                            .call().chatResponse());
            // 계측보다 방어 검사가 먼저다 — record()는 response.getMetadata()를 읽으므로 null이면 여기서 NPE가 난다.
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return question;
            }
            llmMetrics.record(LlmMetrics.PURPOSE_REWRITE, response, System.nanoTime() - start);
            String rewritten = response.getResult().getOutput().getText();
            return (rewritten == null || rewritten.isBlank()) ? question : rewritten.trim();
        } catch (LlmUnavailableException e) {
            llmMetrics.recordDegraded(LlmMetrics.PURPOSE_REWRITE,
                    e.reason().name().toLowerCase(java.util.Locale.ROOT));
            return question;
        } catch (Exception e) {
            // 재작성도 새로운 장애 지점이 되어서는 안 되므로 원래 질문으로 진행한다.
            return question;
        }
    }

    static String buildUserPrompt(String question, List<Message> history) {
        StringBuilder prompt = new StringBuilder("이전 대화:\n");
        if (history != null) {
            history.stream()
                    .filter(message -> message.getMessageType() == MessageType.USER
                            || message.getMessageType() == MessageType.ASSISTANT)
                    .forEach(message -> prompt.append(label(message.getMessageType()))
                            .append(": ")
                            .append(message.getText())
                            .append('\n'));
        }
        return prompt.append("\n마지막 사용자 질문: ")
                .append(question)
                .toString();
    }

    private static String label(MessageType messageType) {
        return switch (messageType) {
            case USER -> "사용자";
            case ASSISTANT -> "도우미";
            default -> throw new IllegalArgumentException("지원하지 않는 메시지 타입입니다.");
        };
    }
}
