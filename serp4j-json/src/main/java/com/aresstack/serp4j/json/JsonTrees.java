package com.aresstack.serp4j.json;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.internal.GsonTreeCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Conversion between JSON text and {@link StructuredValue} (ADR-019 §1, part J1 of {@code serp4j-json}). This is
 * the only place in Serp4J that uses a JSON library; provider modules read responses and mapping definitions
 * through it and work on the tree afterwards.
 *
 * <p>Reading is strict RFC 8259 JSON: one top-level value, no comments, no trailing data. Numbers keep their exact
 * decimal value ({@code BigDecimal}); for duplicate member names the last one wins. Writing produces compact or
 * indented JSON; member order is preserved. All methods are thread-safe.</p>
 */
public final class JsonTrees {

    private JsonTrees() {
    }

    /**
     * Parses JSON text.
     *
     * @throws JsonTreeException if the text is not valid JSON
     */
    public static StructuredValue read(String json) {
        Objects.requireNonNull(json, "json");
        return GsonTreeCodec.read(json);
    }

    /**
     * Parses JSON from a reader; the reader is consumed but not closed.
     *
     * @throws JsonTreeException if the text is not valid JSON
     * @throws IOException       if reading fails
     */
    public static StructuredValue read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        return GsonTreeCodec.read(reader);
    }

    /**
     * Reads a UTF-8 JSON classpath resource relative to {@code anchor} (see {@link Class#getResourceAsStream}),
     * typically a mapping definition of a provider module.
     *
     * @throws JsonTreeException if the resource is missing, unreadable or not valid JSON
     */
    public static StructuredValue readResource(Class<?> anchor, String name) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(name, "name");
        InputStream in = anchor.getResourceAsStream(name);
        if (in == null) {
            throw new JsonTreeException("resource not found: " + name + " (relative to " + anchor.getName() + ")");
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GsonTreeCodec.read(reader);
        } catch (IOException e) {
            throw new JsonTreeException("cannot read resource " + name + ": " + e.getMessage(), e);
        }
    }

    /** Compact JSON. */
    public static String write(StructuredValue value) {
        Objects.requireNonNull(value, "value");
        return GsonTreeCodec.write(value, false);
    }

    /** Indented JSON (two spaces), for golden files and debugging. */
    public static String writePretty(StructuredValue value) {
        Objects.requireNonNull(value, "value");
        return GsonTreeCodec.write(value, true);
    }
}
