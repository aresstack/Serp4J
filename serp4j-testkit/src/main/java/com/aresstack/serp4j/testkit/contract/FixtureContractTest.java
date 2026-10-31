package com.aresstack.serp4j.testkit.contract;

import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.FixtureCatalog;
import com.aresstack.serp4j.testkit.fixture.FixtureSecretScanner;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks every fixture visible on the test classpath (architecture.md §9, implementation-plan.md Paket E): each lies
 * at {@code fixtures/<provider>/<name>}, has a valid sidecar that declares its provenance (decision E-1), and
 * contains no suspected secret. A module with fixtures extends it without further code; override
 * {@link #forbiddenValues()} to add literal values (e.g. the key of a recording run, read from the environment).
 */
public abstract class FixtureContractTest {

    /** Literal values that must not appear in any fixture; default none. */
    protected List<String> forbiddenValues() {
        return Collections.emptyList();
    }

    /** Class loader whose classpath is checked; default the context class loader. */
    protected ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : getClass().getClassLoader();
    }

    @Test
    @DisplayName("every fixture has a valid sidecar with provenance")
    void everyFixtureDeclaresItsProvenance() {
        Fixtures.catalog(classLoader()).assertValid();
    }

    @Test
    @DisplayName("no fixture contains a suspected secret")
    void noFixtureContainsSecrets() {
        FixtureCatalog catalog = Fixtures.catalog(classLoader());
        List<String> forbidden = new ArrayList<String>(forbiddenValues());
        FixtureSecretScanner scanner = FixtureSecretScanner.defaults()
                .forbidding(forbidden.toArray(new String[0]));
        scanner.assertClean(catalog.getFixtures());
    }

    @Test
    @DisplayName("recorded fixtures are anonymized and name their source")
    void recordedFixturesAreAnonymized() {
        for (Fixture fixture : Fixtures.catalog(classLoader()).getFixtures()) {
            if (fixture.isRecorded() && (!fixture.isAnonymized() || !fixture.getSource().isPresent())) {
                throw new AssertionError(fixture.getResourcePath() + " is recorded but not anonymized or has no"
                        + " source");
            }
        }
    }
}
