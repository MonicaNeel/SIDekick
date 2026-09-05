package sidekick.answering.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sidekick.answering.AnswerService;
import sidekick.answering.CitationValidator;
import sidekick.answering.LlmClient;
import sidekick.answering.OpenRouterClient;
import sidekick.answering.PromptAssembler;
import sidekick.retrieval.Retriever;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring wiring at the answering module edge (ADR-003). Loads the prompt
 * templates from the classpath (they are config, owner's decision) and builds
 * the zero-Spring core. The API key comes from the OPENROUTER_API_KEY
 * environment variable — never from a config file.
 */
@Configuration
class AnsweringConfiguration {

    @Bean
    PromptAssembler promptAssembler(
            @Value("${sidekick.answering.system-prompt:/prompts/system.txt}") String systemPath,
            @Value("${sidekick.answering.user-template:/prompts/user-template.txt}") String userPath) {
        return new PromptAssembler(loadResource(systemPath), loadResource(userPath));
    }

    @Bean
    CitationValidator citationValidator() {
        return new CitationValidator();
    }

    @Bean
    LlmClient llmClient(
            @Value("${sidekick.answering.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${OPENROUTER_API_KEY:}") String apiKey,
            @Value("${sidekick.answering.model}") String model,
            @Value("${sidekick.answering.max-attempts:5}") int maxAttempts,
            @Value("${sidekick.answering.initial-backoff-millis:2000}") long initialBackoffMillis) {
        return new OpenRouterClient(baseUrl, apiKey, model, maxAttempts, initialBackoffMillis);
    }

    @Bean
    AnswerService answerService(Retriever retriever, PromptAssembler prompts, LlmClient llm,
                                CitationValidator validator,
                                @Value("${sidekick.answering.top-k:6}") int topK,
                                @Value("${sidekick.answering.min-top-score:0.5}") double minTopScore,
                                @Value("${sidekick.answering.model}") String modelName) {
        return new AnswerService(retriever, prompts, llm, validator,
                new AnswerService.Config(topK, minTopScore, modelName));
    }

    private static String loadResource(String classpathLocation) {
        try (InputStream in = AnsweringConfiguration.class.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("prompt template not found on classpath: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read prompt template " + classpathLocation, e);
        }
    }
}
