package sidekick.answering;

import sidekick.retrieval.ScoredChunk;

import java.util.List;

/**
 * Builds the two prompts from config-file templates (owner's decision,
 * 2026-08-24): plain String.replace on {placeholders}, no template engine.
 * The templates are loaded at the module edge and passed in as strings — this
 * core stays free of Spring and file-system concerns.
 *
 * Prompt-injection note: {question} is UNTRUSTED user input. The template
 * restates the rules AFTER the question so a "ignore your instructions"
 * attempt is followed by the rules again; the citation validator (gate 2)
 * remains the enforcement backstop either way.
 */
public final class PromptAssembler {

    private final String systemTemplate;
    private final String userTemplate;

    public PromptAssembler(String systemTemplate, String userTemplate) {
        this.systemTemplate = systemTemplate;
        this.userTemplate = userTemplate;
    }

    public String systemPrompt() {
        return systemTemplate;
    }

    public String userPrompt(String fundName, String question, List<ScoredChunk> chunks) {
        return userTemplate
                .replace("{fund_name}", fundName)
                .replace("{chunks}", renderChunks(chunks))
                .replace("{question}", question);
    }

    /** Excerpts numbered 1..n — the numbers the model must cite and the validator checks. */
    private static String renderChunks(List<ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk chunk = chunks.get(i);
            sb.append('[').append(i + 1).append("] (section: ").append(chunk.section())
                    .append(", pages ").append(chunk.page()).append('-').append(chunk.endPage())
                    .append(")\n")
                    .append(chunk.text().strip())
                    .append("\n\n");
        }
        return sb.toString().strip();
    }
}
