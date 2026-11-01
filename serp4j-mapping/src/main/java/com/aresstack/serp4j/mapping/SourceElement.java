package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.mapping.path.PathLocation;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read access for custom mappers to one element of the provider response (or to the root for metadata mappers),
 * with consumption tracking (ADR-019 §4): every value read through {@link #get}, {@link #text} or {@link #select}
 * counts as consumed; everything else ends up in {@code Extensions.unmapped}. Reading through
 * {@link #getValue()} does <em>not</em> consume anything – call {@link #consume(String)} for parts used that way.
 *
 * <p>Paths are relative to this element and use the syntax of {@link TreePath}. JSON {@code null} counts as
 * "no value". Instances belong to one mapping run and are not thread-safe.</p>
 */
public final class SourceElement {

    private final StructuredValue value;
    private final PathLocation location;
    private final PathLocation base;
    private final Set<PathLocation> consumed;

    SourceElement(StructuredValue value, PathLocation location, PathLocation base, Set<PathLocation> consumed) {
        this.value = value;
        this.location = location;
        this.base = base;
        this.consumed = consumed;
    }

    /** The raw value of the element; reading it does not mark anything as consumed. */
    public StructuredValue getValue() {
        return value;
    }

    /** Location of the element in the provider response, for example {@code $.items[3]}. */
    public PathLocation getLocation() {
        return location;
    }

    /** The first non-null value at {@code path}; marks it as consumed. */
    public Optional<StructuredValue> get(String path) {
        for (TreePath.Match m : TreePath.parse(path).locate(value)) {
            if (!m.getValue().isNull()) {
                mark(m.getLocation());
                return Optional.of(m.getValue());
            }
            mark(m.getLocation());
        }
        return Optional.empty();
    }

    /**
     * The first value at {@code path} as text (strings as they are, numbers in plain notation, booleans as
     * {@code true}/{@code false}); objects and arrays yield empty. Marks the value as consumed.
     */
    public Optional<String> text(String path) {
        Optional<StructuredValue> v = get(path);
        if (!v.isPresent()) {
            return Optional.empty();
        }
        StructuredValue s = v.get();
        switch (s.getKind()) {
            case STRING:
                return s.asText();
            case NUMBER:
                return Optional.of(s.asNumber().get().toPlainString());
            case BOOLEAN:
                return Optional.of(s.asBoolean().get().toString());
            default:
                return Optional.empty();
        }
    }

    /** All non-null values at {@code path} (with {@code [*]} possibly many); marks all matches as consumed. */
    public List<StructuredValue> select(String path) {
        List<StructuredValue> out = new ArrayList<StructuredValue>();
        for (TreePath.Match m : TreePath.parse(path).locate(value)) {
            mark(m.getLocation());
            if (!m.getValue().isNull()) {
                out.add(m.getValue());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The values at {@code path} as nested elements that share this element's consumption tracking. Nothing is
     * consumed until a nested element is read, so fields a mapper ignores stay in the unmapped rest – for example
     * knowledge-graph rows of a type the mapper does not handle.
     */
    public List<SourceElement> elements(String path) {
        List<SourceElement> out = new ArrayList<SourceElement>();
        for (TreePath.Match m : TreePath.parse(path).locate(value)) {
            if (!m.getValue().isNull()) {
                out.add(new SourceElement(m.getValue(), location.resolve(m.getLocation()),
                        base.resolve(m.getLocation()), consumed));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Marks every value at {@code path} as consumed without reading it. */
    public SourceElement consume(String path) {
        for (TreePath.Match m : TreePath.parse(path).locate(value)) {
            mark(m.getLocation());
        }
        return this;
    }

    /** Marks the whole element as consumed. */
    public SourceElement consumeAll() {
        consumed.add(base);
        return this;
    }

    void mark(PathLocation relative) {
        consumed.add(base.resolve(relative));
    }

    @Override
    public String toString() {
        return "SourceElement{" + location + '}';
    }

    static SourceElement root(StructuredValue value, Set<PathLocation> consumed) {
        return new SourceElement(Objects.requireNonNull(value, "value"), PathLocation.root(), PathLocation.root(),
                consumed);
    }

    static SourceElement at(StructuredValue value, PathLocation location, Set<PathLocation> consumed) {
        return new SourceElement(value, location, PathLocation.root(), consumed);
    }
}
