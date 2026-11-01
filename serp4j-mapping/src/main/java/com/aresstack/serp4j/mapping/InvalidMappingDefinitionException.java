package com.aresstack.serp4j.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when a mapping definition is invalid – the only exception of the mapping infrastructure that concerns
 * the definition rather than a programming error (ADR-019 §4). It is raised while loading or binding a definition,
 * never while mapping a response. {@link #getProblems()} lists every problem found, not only the first.
 */
@SuppressWarnings("serial")
public class InvalidMappingDefinitionException extends IllegalArgumentException {

    private final List<String> problems;

    public InvalidMappingDefinitionException(String definition, List<String> problems) {
        super(message(definition, problems));
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    private static String message(String definition, List<String> problems) {
        StringBuilder out = new StringBuilder("invalid mapping definition '").append(definition).append("': ")
                .append(problems.size()).append(problems.size() == 1 ? " problem" : " problems");
        for (String problem : problems) {
            out.append("\n  - ").append(problem);
        }
        return out.toString();
    }

    /** All problems, each with the location inside the definition, for example {@code sections[1].fields.url}. */
    public List<String> getProblems() {
        return problems;
    }
}
