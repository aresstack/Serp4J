package com.aresstack.serp4j.mapping.path;

import com.aresstack.serp4j.core.model.ext.StructuredValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A path into a {@link StructuredValue} tree in the deliberately small path language of the mapping
 * definitions (ADR-019, architecture.md §5.6 rule 2).
 *
 * <pre>
 *   path     := '$' step* | first step*
 *   first    := name | index
 *   step     := '.' name | index
 *   name     := [A-Za-z0-9_-]+ | "'" any character except "'" "'"
 *   index    := '[' digits ']' | '[*]'
 * </pre>
 *
 * <p>Examples: {@code $.organic_results[*]}, {@code tasks[0].result[0].items[*]}, {@code thumbnail.src},
 * {@code $.'@graph'[0]}, {@code $} (the value itself). A leading {@code $} is optional; inside a section, paths
 * are always evaluated relative to the current element. {@code [*]} fans out over all elements of an array, so a
 * path can have any number of matches. There are no filters, no recursive descent and no expressions – what this
 * language cannot express belongs into a custom mapper written in Java.</p>
 *
 * <p>Resolution never fails: a step that does not fit the value (a name on an array, an index on an object, an
 * index out of range) simply yields no match. Instances are immutable and thread-safe.</p>
 */
public final class TreePath {

    private static final TreePath ROOT = new TreePath("$", Collections.<Step>emptyList());

    private final String expression;
    private final List<Step> steps;

    private TreePath(String expression, List<Step> steps) {
        this.expression = expression;
        this.steps = steps;
    }

    /** The path that selects the value it is applied to ({@code $}). */
    public static TreePath root() {
        return ROOT;
    }

    /**
     * Parses a path expression.
     *
     * @throws IllegalArgumentException if {@code expression} is not a valid path; the message names the position
     */
    public static TreePath parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        String text = expression.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        List<Step> steps = new ArrayList<Step>();
        int i = 0;
        boolean expectName;
        if (text.charAt(0) == '$') {
            i = 1;
            expectName = false;
        } else {
            expectName = true;
        }
        while (i < text.length()) {
            char c = text.charAt(i);
            if (expectName) {
                i = parseName(text, i, steps);
                expectName = false;
            } else if (c == '.') {
                if (i + 1 >= text.length() || text.charAt(i + 1) == '[') {
                    throw error(expression, i, "name expected after '.'");
                }
                i = parseName(text, i + 1, steps);
            } else if (c == '[') {
                int close = text.indexOf(']', i);
                if (close < 0) {
                    throw error(expression, i, "missing ']'");
                }
                String inner = text.substring(i + 1, close);
                if (inner.equals("*")) {
                    steps.add(Step.WILDCARD);
                } else if (isDigits(inner)) {
                    steps.add(Step.index(Integer.parseInt(inner)));
                } else {
                    throw error(expression, i, "index must be a non-negative number or '*' but was '" + inner + "'");
                }
                i = close + 1;
            } else {
                throw error(expression, i, "'.' or '[' expected but found '" + c + "'");
            }
        }
        if (steps.isEmpty() && text.equals("$")) {
            return ROOT;
        }
        return new TreePath(text, Collections.unmodifiableList(steps));
    }

    private static int parseName(String text, int start, List<Step> steps) {
        if (start >= text.length()) {
            throw error(text, start, "name expected");
        }
        if (text.charAt(start) == '\'') {
            int close = text.indexOf('\'', start + 1);
            if (close < 0) {
                throw error(text, start, "missing closing quote");
            }
            if (close == start + 1) {
                throw error(text, start, "quoted name must not be empty");
            }
            steps.add(Step.name(text.substring(start + 1, close)));
            return close + 1;
        }
        if (text.charAt(start) == '[') {
            // "[0].x" at the very start of a relative path: no name, the bracket is handled by the caller loop.
            return start;
        }
        int end = start;
        while (end < text.length() && isNameChar(text.charAt(end))) {
            end++;
        }
        if (end == start) {
            throw error(text, start, "name expected but found '" + text.charAt(start) + "'");
        }
        steps.add(Step.name(text.substring(start, end)));
        return end;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty() || s.length() > 9) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException error(String expression, int position, String message) {
        return new IllegalArgumentException("invalid path '" + expression + "' at position " + position + ": " + message);
    }

    /** {@code true} for {@code $}, which selects the value itself. */
    public boolean isRoot() {
        return steps.isEmpty();
    }

    /** {@code true} if the path contains {@code [*]} and can therefore have more than one match. */
    public boolean isMultiValued() {
        for (Step step : steps) {
            if (step.kind == Step.Kind.WILDCARD) {
                return true;
            }
        }
        return false;
    }

    /** All matches in document order, each with its concrete location; empty if the path does not resolve. */
    public List<Match> locate(StructuredValue value) {
        Objects.requireNonNull(value, "value");
        List<Match> current = Collections.singletonList(new Match(PathLocation.root(), value));
        for (Step step : steps) {
            List<Match> next = new ArrayList<Match>();
            for (Match m : current) {
                step.apply(m, next);
            }
            if (next.isEmpty()) {
                return Collections.emptyList();
            }
            current = next;
        }
        return Collections.unmodifiableList(current);
    }

    /** All matching values in document order. */
    public List<StructuredValue> select(StructuredValue value) {
        List<Match> matches = locate(value);
        List<StructuredValue> out = new ArrayList<StructuredValue>(matches.size());
        for (Match m : matches) {
            out.add(m.getValue());
        }
        return Collections.unmodifiableList(out);
    }

    /** The path as written (trimmed). */
    @Override
    public String toString() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TreePath && steps.equals(((TreePath) o).steps));
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    /** One match of a path: the value and where it was found. */
    public static final class Match {
        private final PathLocation location;
        private final StructuredValue value;

        Match(PathLocation location, StructuredValue value) {
            this.location = location;
            this.value = value;
        }

        public PathLocation getLocation() {
            return location;
        }

        public StructuredValue getValue() {
            return value;
        }

        @Override
        public String toString() {
            return location + "=" + value;
        }
    }

    private static final class Step {
        enum Kind { NAME, INDEX, WILDCARD }

        static final Step WILDCARD = new Step(Kind.WILDCARD, null, -1);

        final Kind kind;
        final String name;
        final int index;

        private Step(Kind kind, String name, int index) {
            this.kind = kind;
            this.name = name;
            this.index = index;
        }

        static Step name(String name) {
            return new Step(Kind.NAME, name, -1);
        }

        static Step index(int index) {
            return new Step(Kind.INDEX, null, index);
        }

        void apply(Match m, List<Match> out) {
            StructuredValue v = m.getValue();
            switch (kind) {
                case NAME:
                    Map<String, StructuredValue> members = v.getMembers();
                    StructuredValue child = members.get(name);
                    if (child != null) {
                        out.add(new Match(m.getLocation().child(name), child));
                    }
                    break;
                case INDEX:
                    List<StructuredValue> elements = v.getElements();
                    if (v.isArray() && index < elements.size()) {
                        out.add(new Match(m.getLocation().child(index), elements.get(index)));
                    }
                    break;
                default:
                    List<StructuredValue> all = v.getElements();
                    for (int i = 0; i < all.size(); i++) {
                        out.add(new Match(m.getLocation().child(i), all.get(i)));
                    }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Step)) {
                return false;
            }
            Step s = (Step) o;
            return kind == s.kind && index == s.index && Objects.equals(name, s.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, name, index);
        }
    }
}
