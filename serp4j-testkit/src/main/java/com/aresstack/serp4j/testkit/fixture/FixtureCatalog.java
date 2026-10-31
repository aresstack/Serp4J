package com.aresstack.serp4j.testkit.fixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of {@link Fixtures#catalog()}: the loadable fixtures and every file that breaks the layout rules. */
public final class FixtureCatalog {

    private final List<Fixture> fixtures;
    private final List<String> problems;

    FixtureCatalog(List<Fixture> fixtures, List<String> problems) {
        this.fixtures = Collections.unmodifiableList(new ArrayList<Fixture>(fixtures));
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    public List<Fixture> getFixtures() {
        return fixtures;
    }

    /** Fixtures without or with invalid sidecar, orphaned sidecars, files outside {@code fixtures/<provider>/}. */
    public List<String> getProblems() {
        return problems;
    }

    /**
     * Fails if any file breaks the layout rules.
     *
     * @throws AssertionError listing the problems
     */
    public void assertValid() {
        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder("invalid fixtures:");
            for (String problem : problems) {
                message.append("\n  ").append(problem);
            }
            throw new AssertionError(message.toString());
        }
    }

    @Override
    public String toString() {
        return "FixtureCatalog{fixtures=" + fixtures.size() + ", problems=" + problems.size() + '}';
    }
}
