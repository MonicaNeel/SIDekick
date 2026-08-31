package sidekick.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sidekick.ingestion.internal.ChunkRepository;

import java.util.List;

/**
 * Public read API over the chunks table (used by retrieval to build its
 * in-memory index). Other modules ask here; the entities stay internal.
 */
@Service
public class ChunkFinder {

    private final ChunkRepository chunks;

    public ChunkFinder(ChunkRepository chunks) {
        this.chunks = chunks;
    }

    @Transactional(readOnly = true)
    public List<StoredChunk> findAll() {
        return chunks.findAll().stream()
                .map(c -> new StoredChunk(c.getId(), c.getFundId(), c.getSection(),
                        c.getPage(), c.getText(), c.getEmbedding()))
                .toList();
    }
}
