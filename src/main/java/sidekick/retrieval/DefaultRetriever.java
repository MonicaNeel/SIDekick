package sidekick.retrieval;

import sidekick.embedding.TextEmbedder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The query path: embed the question (one way or two), search the index.
 * Zero Spring imports.
 *
 * queryPrefix is the bge instruction prefix ("Represent this sentence for
 * searching relevant passages: ") the model was trained with for short
 * retrieval queries. See QueryMode for when each mode wins.
 */
public final class DefaultRetriever implements Retriever {

    private final TextEmbedder embedder;
    private final InMemoryVectorIndex index;
    private final String queryPrefix;
    private final QueryMode mode;
    private final int maxPerSection;

    /**
     * @param maxPerSection cap on results from any one section (0 = off).
     *                      Overlapping windows make same-section near-
     *                      duplicates that hog top-k seats (measured: 4 of 5
     *                      seats on one query); capping trades redundancy for
     *                      diversity.
     */
    public DefaultRetriever(TextEmbedder embedder, InMemoryVectorIndex index,
                            String queryPrefix, QueryMode mode, int maxPerSection) {
        this.embedder = embedder;
        this.index = index;
        this.queryPrefix = queryPrefix == null ? "" : queryPrefix;
        this.mode = mode;
        this.maxPerSection = maxPerSection;
    }

    @Override
    public List<ScoredChunk> search(String question, String fundId, int topK) {
        // Overfetch when capping: the cap discards some of the raw top list,
        // so it needs a deeper pool to fill topK seats from.
        int fetch = maxPerSection > 0 ? topK * 4 : topK;
        List<ScoredChunk> ranked = switch (mode) {
            case PLAIN -> index.search(embedder.embed(question), fundId, fetch);
            case PREFIXED -> index.search(embedder.embed(queryPrefix + question), fundId, fetch);
            case FUSED -> fused(question, fundId, fetch);
        };
        return capPerSection(ranked, topK);
    }

    private List<ScoredChunk> capPerSection(List<ScoredChunk> ranked, int topK) {
        if (maxPerSection <= 0) {
            return ranked.subList(0, Math.min(topK, ranked.size()));
        }
        Map<String, Integer> perSection = new LinkedHashMap<>();
        List<ScoredChunk> result = new ArrayList<>(topK);
        for (ScoredChunk chunk : ranked) {
            if (perSection.merge(chunk.section(), 1, Integer::sum) <= maxPerSection) {
                result.add(chunk);
                if (result.size() == topK) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Reciprocal Rank Fusion: order by each chunk's RANK in the two result
     * lists (1/(60+rank), summed), because the two variants' raw scores live
     * on different scales and max-score fusion lets one variant drown the
     * other (measured: it reproduced PLAIN's results exactly). The returned
     * ScoredChunk still carries the chunk's best cosine, so downstream
     * thresholds keep real similarity units.
     */
    private List<ScoredChunk> fused(String question, String fundId, int topK) {
        Map<String, Double> rrfScore = new LinkedHashMap<>();
        Map<String, ScoredChunk> bestByChunk = new LinkedHashMap<>();
        for (float[] queryVector : new float[][]{
                embedder.embed(question),
                embedder.embed(queryPrefix + question)}) {
            List<ScoredChunk> results = index.search(queryVector, fundId, topK);
            for (int rank = 0; rank < results.size(); rank++) {
                ScoredChunk result = results.get(rank);
                rrfScore.merge(result.chunkId(), 1.0 / (60 + rank + 1), Double::sum);
                bestByChunk.merge(result.chunkId(), result,
                        (a, b) -> a.score() >= b.score() ? a : b);
            }
        }
        List<ScoredChunk> merged = new ArrayList<>(bestByChunk.values());
        merged.sort(Comparator.comparingDouble(
                (ScoredChunk c) -> rrfScore.get(c.chunkId())).reversed());
        return merged.subList(0, Math.min(topK, merged.size()));
    }
}
