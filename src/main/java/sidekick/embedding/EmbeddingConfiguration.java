package sidekick.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Spring wiring at the module edge (ADR-003): the embedding core
 * (OnnxTextEmbedder) has zero Spring imports; this class alone knows how to
 * build it from configuration. Model, tokenizer, and pooling are config —
 * a model swap touches application.yml, not code (ADR-001 hard rule).
 *
 * `sidekick.embedding.stub=true` disables this bean so tests can supply a
 * fast fake instead of loading the 127 MB ONNX model.
 */
@Configuration
public class EmbeddingConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "sidekick.embedding.stub", havingValue = "false", matchIfMissing = true)
    TextEmbedder textEmbedder(
            @Value("${sidekick.embedding.model-path}") Path modelPath,
            @Value("${sidekick.embedding.tokenizer-path}") Path tokenizerPath,
            @Value("${sidekick.embedding.pooling}") Pooling pooling) {
        return new OnnxTextEmbedder(modelPath, tokenizerPath, pooling);
    }
}
