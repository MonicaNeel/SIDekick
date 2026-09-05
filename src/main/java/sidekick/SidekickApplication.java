package sidekick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application root. Every package directly under `sidekick` is a Spring
 * Modulith module (eval, ingestion, embedding, later: retrieval, answering,
 * catalog, web); each module's root package is its public API and `internal`
 * subpackages are invisible to other modules — enforced by ModularityTest.
 */
@SpringBootApplication
public class SidekickApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(SidekickApplication.class, args);
        // Web runs keep serving. One-shot CLI runs (ingest/eval/ask) pass
        // --spring.main.web-application-type=none and must close the context
        // explicitly, because ONNX/tokenizer hold non-daemon threads that
        // would otherwise keep the JVM alive forever.
        if (!(context instanceof org.springframework.web.context.WebApplicationContext)) {
            context.close();
        }
    }
}
