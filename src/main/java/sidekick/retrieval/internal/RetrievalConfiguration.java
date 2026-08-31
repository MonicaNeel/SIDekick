package sidekick.retrieval.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import sidekick.embedding.TextEmbedder;
import sidekick.retrieval.DefaultRetriever;
import sidekick.retrieval.InMemoryVectorIndex;
import sidekick.retrieval.Retriever;

/**
 * Spring wiring at the retrieval module edge (ADR-003) — the core classes it
 * constructs contain zero Spring imports.
 *
 * @EnableAsync is required by @ApplicationModuleListener (IndexLoader), which
 * runs listeners on a separate thread after the publishing transaction commits.
 */
@Configuration
@EnableAsync
class RetrievalConfiguration {

    @Bean
    InMemoryVectorIndex vectorIndex() {
        return new InMemoryVectorIndex();
    }

    @Bean
    Retriever retriever(TextEmbedder embedder, InMemoryVectorIndex index,
                        @Value("${sidekick.retrieval.query-prefix:}") String queryPrefix) {
        return new DefaultRetriever(embedder, index, queryPrefix);
    }
}
