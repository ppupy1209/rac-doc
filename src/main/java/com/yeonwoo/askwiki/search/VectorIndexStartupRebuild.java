package com.yeonwoo.askwiki.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Owns the startup rebuild policy for the active vector index.
 *
 * <p>The policy lives in its own bean rather than on the index implementations because
 * {@link com.yeonwoo.askwiki.config.VectorIndexConfig} re-exposes the chosen implementation under a
 * second bean name, and Spring registers {@code @EventListener} methods <em>per bean name</em>. A
 * listener on the implementation itself therefore ran twice per startup — measured as two full
 * {@code select … from chunk order by id} loads in memory mode, and two Elasticsearch rebuilds with
 * hybrid search on.
 */
@Component
public class VectorIndexStartupRebuild {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexStartupRebuild.class);

    private final VectorIndex activeVectorIndex;
    private final String implementation;
    private final boolean hybridSearchEnabled;

    public VectorIndexStartupRebuild(VectorIndex activeVectorIndex,
                                     @Value("${askwiki.vector-index.impl:memory}") String implementation,
                                     @Value("${askwiki.search.hybrid:false}") boolean hybridSearchEnabled) {
        this.activeVectorIndex = activeVectorIndex;
        this.implementation = implementation;
        this.hybridSearchEnabled = hybridSearchEnabled;
    }

    /**
     * The in-memory index lives in the heap, so every startup has to load it from MySQL. Elasticsearch
     * outlives the process and keeps C1's zero-rebuild startup — except with hybrid search on, where the
     * index must be known to agree with MySQL before BM25 can match anything (see
     * {@link EsVectorIndex#search}). Elasticsearch is intentionally lazy at startup, so a temporary
     * outage must not stop the app.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildActiveIndex() {
        if ("memory".equals(implementation)) {
            log.info("Rebuilt in-memory vector index with {} chunks", activeVectorIndex.rebuild());
            return;
        }
        if (!hybridSearchEnabled) {
            return;
        }
        try {
            log.info("Rebuilt Elasticsearch index with {} chunks for hybrid search", activeVectorIndex.rebuild());
        } catch (RuntimeException exception) {
            log.warn("Could not rebuild Elasticsearch index for hybrid search; continuing startup", exception);
        }
    }
}
