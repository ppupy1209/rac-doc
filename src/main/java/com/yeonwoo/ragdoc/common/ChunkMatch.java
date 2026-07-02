package com.yeonwoo.ragdoc.common;

public record ChunkMatch(
        Long chunkId,
        Long documentId,
        String title,
        int seq,
        String content,
        double score
) {
}
