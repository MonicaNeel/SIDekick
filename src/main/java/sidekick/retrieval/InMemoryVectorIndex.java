package sidekick.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The hand-rolled vector index (PLAN §4, ADR-002): all chunk vectors in RAM,
 * searched by brute-force dot product. Zero Spring imports — plain Java,
 * wired at the module edge.
 *
 * Concurrency model: readers never lock. The entire index lives behind one
 * AtomicReference; a reload builds a complete new snapshot and swaps the
 * reference in a single atomic step, so a concurrent search sees either the
 * whole old index or the whole new one — never a half-built hybrid.
 */
public final class InMemoryVectorIndex {

    /** One indexed chunk: metadata plus its (L2-normalized) vector. */
    public record Entry(String chunkId, String fundId, String section, int page,
                        int endPage, String text, float[] vector) {
    }

    private record Snapshot(List<Entry> entries) {
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(List.of()));

    /** Atomically replaces the whole index (the "swap the binder" move). */
    public void replaceAll(List<Entry> entries) {
        current.set(new Snapshot(List.copyOf(entries)));
    }

    public int size() {
        return current.get().entries().size();
    }

    /**
     * Brute-force cosine search: one dot product per (fund-matching) chunk,
     * keeping the best topK in a small min-heap. ~5k chunks x 384 dims is
     * ~2M multiplications — well under a millisecond on any modern CPU.
     */
    public List<ScoredChunk> search(float[] queryVector, String fundId, int topK) {
        // Min-heap of the best-so-far: the WORST of the current top-k sits on
        // top, so each new score only competes against that one.
        PriorityQueue<ScoredChunk> best = new PriorityQueue<>(Comparator.comparingDouble(ScoredChunk::score));
        for (Entry entry : current.get().entries()) {
            if (fundId != null && !fundId.equals(entry.fundId())) {
                continue;
            }
            double score = dot(queryVector, entry.vector());
            if (best.size() < topK) {
                best.add(toResult(entry, score));
            } else if (score > best.peek().score()) {
                best.poll();
                best.add(toResult(entry, score));
            }
        }
        List<ScoredChunk> results = new ArrayList<>(best);
        results.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return results;
    }

    private static ScoredChunk toResult(Entry entry, double score) {
        return new ScoredChunk(entry.chunkId(), entry.fundId(), entry.section(),
                entry.page(), entry.endPage(), entry.text(), score);
    }

    /** Cosine similarity of normalized vectors = plain dot product. */
    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}
