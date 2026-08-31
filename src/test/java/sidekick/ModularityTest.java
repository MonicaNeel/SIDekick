package sidekick;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * CLAUDE.md hard constraint: this must always pass. It fails the build if a
 * module reaches into another module's `internal` package or if modules form
 * a dependency cycle — the boundaries are enforced, not aspirational.
 */
class ModularityTest {

    @Test
    void moduleBoundariesHold() {
        ApplicationModules modules = ApplicationModules.of(SidekickApplication.class);
        modules.forEach(m -> System.out.println("module: " + m.getIdentifier()));
        modules.verify();
    }
}
