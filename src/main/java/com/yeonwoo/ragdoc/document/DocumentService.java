package com.yeonwoo.ragdoc.document;

import com.yeonwoo.ragdoc.common.CreateDocumentRequest;
import com.yeonwoo.ragdoc.common.CreateDocumentResponse;
import com.yeonwoo.ragdoc.common.DocumentSummary;
import com.yeonwoo.ragdoc.embedding.EmbeddingClient;
import com.yeonwoo.ragdoc.embedding.EmbeddingCodec;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingCodec embeddingCodec;

    public DocumentService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            Chunker chunker,
            EmbeddingClient embeddingClient,
            EmbeddingCodec embeddingCodec
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.embeddingCodec = embeddingCodec;
    }

    @Transactional
    public CreateDocumentResponse create(CreateDocumentRequest request) {
        Document document = documentRepository.save(new Document(request.title(), null));
        List<String> chunks = chunker.split(request.content());

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            float[] embedding = embeddingClient.embed(content);
            chunkRepository.save(new Chunk(
                    document.getId(),
                    i + 1,
                    content,
                    embeddingCodec.serialize(embedding),
                    approximateTokenCount(content)
            ));
        }

        return new CreateDocumentResponse(document.getId(), document.getTitle(), chunks.size());
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> list() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(document -> new DocumentSummary(
                        document.getId(),
                        document.getTitle(),
                        Math.toIntExact(chunkRepository.countByDocumentId(document.getId())),
                        document.getCreatedAt().toString()
                ))
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        documentRepository.deleteById(id);
    }

    private int approximateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
