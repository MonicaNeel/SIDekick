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
        // close() makes CLI runs exit cleanly (ONNX/tokenizer hold non-daemon
        // threads that otherwise keep the JVM alive). REVISIT at build-order
        // step 5: once the web module adds a server, this must become a plain
        // run() or the app would shut down immediately after startup.
        SpringApplication.run(SidekickApplication.class, args).close();
    }
}
