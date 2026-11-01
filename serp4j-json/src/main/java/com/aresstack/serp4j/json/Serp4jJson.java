package com.aresstack.serp4j.json;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.port.conversation.SearchConversation;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.json.internal.ModelCodec;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes the Serp4J domain model as JSON (ADR-013, part J2 of {@code serp4j-json}): {@link SearchResponse},
 * {@link SearchConversation} and {@link SessionHandle}, for caching responses and persisting dialogues.
 *
 * <p>Every top-level document starts with {@code formatVersion} and {@code type}
 * ({@code search_response}, {@code search_conversation}, {@code session_handle}):</p>
 * <pre>{"formatVersion":1,"type":"search_response","data":{...},"metadata":{...}}</pre>
 *
 * <p>Rules (ADR-013):</p>
 * <ul>
 *   <li><b>Versions.</b> The writer always writes {@link #CURRENT_FORMAT_VERSION}. The reader accepts every version
 *       in {@link #SUPPORTED_FORMAT_VERSIONS}; a missing, non-integer or unknown version (including a newer one) is
 *       rejected with {@link UnsupportedFormatVersionException}, never guessed.</li>
 *   <li><b>Unknown members</b> are ignored when reading, at every level, so newer writers of the same format
 *       version stay readable.</li>
 *   <li><b>Raw payload.</b> {@code SearchMetadata.getRawPayload()} is <em>not</em> written by default; persisted
 *       documents contain no raw provider responses. {@link Builder#includeRawPayload(boolean)} opts in. A raw
 *       payload present in a document is always read.</li>
 *   <li><b>Not part of the format:</b> provider request options of a conversation turn (provider-specific runtime
 *       configuration) are not written. Typed extensions are written for value types {@code String},
 *       {@code Boolean}, {@code Integer}, {@code Long}, {@code Double}, {@code BigDecimal} and
 *       {@code StructuredValue}; any other extension type makes writing fail with {@link Serp4jJsonException}
 *       rather than losing data silently. Unmapped remainders are always written.</li>
 * </ul>
 *
 * <p>Instances are immutable and thread-safe. Invalid JSON text raises {@link JsonTreeException}, a JSON document
 * that is not a valid model document raises {@link Serp4jJsonException} (a subtype) naming the JSON path.</p>
 */
public final class Serp4jJson {

    /** The format version this library writes. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    /** The format versions this library reads. */
    public static final Set<Integer> SUPPORTED_FORMAT_VERSIONS =
            Collections.unmodifiableSet(new LinkedHashSet<Integer>(Arrays.asList(1)));

    static final String TYPE_SEARCH_RESPONSE = "search_response";
    static final String TYPE_SEARCH_CONVERSATION = "search_conversation";
    static final String TYPE_SESSION_HANDLE = "session_handle";

    private static final Serp4jJson DEFAULT = builder().build();

    private final boolean includeRawPayload;
    private final boolean prettyPrint;
    private final ModelCodec codec;

    private Serp4jJson(Builder builder) {
        this.includeRawPayload = builder.includeRawPayload;
        this.prettyPrint = builder.prettyPrint;
        this.codec = new ModelCodec(includeRawPayload);
    }

    /** Default settings: compact output, raw payloads are not written. */
    public static Serp4jJson create() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isIncludeRawPayload() {
        return includeRawPayload;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    // ---- SearchResponse

    public String write(SearchResponse response) {
        Objects.requireNonNull(response, "response");
        return text(TYPE_SEARCH_RESPONSE, codec.writeResponse(response));
    }

    public SearchResponse readSearchResponse(String json) {
        Objects.requireNonNull(json, "json");
        return model(() -> codec.readResponse(document(JsonTrees.read(json), TYPE_SEARCH_RESPONSE)));
    }

    /** Reads from a reader; the reader is consumed but not closed. */
    public SearchResponse readSearchResponse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        StructuredValue tree = JsonTrees.read(reader);
        return model(() -> codec.readResponse(document(tree, TYPE_SEARCH_RESPONSE)));
    }

    // ---- SearchConversation

    public String write(SearchConversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
        return text(TYPE_SEARCH_CONVERSATION, codec.writeConversation(conversation));
    }

    public SearchConversation readSearchConversation(String json) {
        Objects.requireNonNull(json, "json");
        return model(() -> codec.readConversation(document(JsonTrees.read(json), TYPE_SEARCH_CONVERSATION)));
    }

    /** Reads from a reader; the reader is consumed but not closed. */
    public SearchConversation readSearchConversation(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        StructuredValue tree = JsonTrees.read(reader);
        return model(() -> codec.readConversation(document(tree, TYPE_SEARCH_CONVERSATION)));
    }

    // ---- SessionHandle

    public String write(SessionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return text(TYPE_SESSION_HANDLE, codec.writeSessionHandle(handle));
    }

    public SessionHandle readSessionHandle(String json) {
        Objects.requireNonNull(json, "json");
        return model(() -> codec.readSessionHandle(document(JsonTrees.read(json), TYPE_SESSION_HANDLE)));
    }

    /** Reads from a reader; the reader is consumed but not closed. */
    public SessionHandle readSessionHandle(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        StructuredValue tree = JsonTrees.read(reader);
        return model(() -> codec.readSessionHandle(document(tree, TYPE_SESSION_HANDLE)));
    }

    // ---- internals

    private interface ModelRead<T> {
        T read();
    }

    /** Reports violated model invariants (thrown by the core builders) as invalid documents. */
    private static <T> T model(ModelRead<T> read) {
        try {
            return read.read();
        } catch (JsonTreeException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            throw new Serp4jJsonException("invalid model document: " + e.getMessage(), e);
        }
    }

    private String text(String type, StructuredValue body) {
        StructuredValue document = ModelCodec.envelope(CURRENT_FORMAT_VERSION, type, body);
        return prettyPrint ? JsonTrees.writePretty(document) : JsonTrees.write(document);
    }

    /**
     * Validates version and type of a top-level document. Only version 1 exists; a later version adds its migration
     * here (read older documents into the current model).
     */
    private static StructuredValue document(StructuredValue document, String expectedType) {
        if (!document.isObject()) {
            throw new Serp4jJsonException("$: expected a JSON object but found "
                    + document.getKind().name().toLowerCase(java.util.Locale.ROOT));
        }
        StructuredValue version = document.get("formatVersion").orElse(null);
        if (version == null || version.isNull()) {
            throw new UnsupportedFormatVersionException("$.formatVersion: missing; Serp4J JSON documents must declare"
                    + " their format version (supported: " + SUPPORTED_FORMAT_VERSIONS + ")", null);
        }
        Integer number = null;
        if (version.isNumber()) {
            BigDecimal value = version.asNumber().get();
            try {
                number = value.intValueExact();
            } catch (ArithmeticException e) {
                number = null;
            }
        }
        if (number == null) {
            throw new UnsupportedFormatVersionException("$.formatVersion: expected an integer but found "
                    + version.getKind().name().toLowerCase(java.util.Locale.ROOT) + " (supported: "
                    + SUPPORTED_FORMAT_VERSIONS + ")", null);
        }
        if (!SUPPORTED_FORMAT_VERSIONS.contains(number)) {
            throw new UnsupportedFormatVersionException("$.formatVersion: unsupported version " + number
                    + " (supported: " + SUPPORTED_FORMAT_VERSIONS + ")", number);
        }
        ModelCodec.requireType(document, expectedType);
        return document;
    }

    /** Settings for a {@link Serp4jJson} instance. */
    public static final class Builder {

        private boolean includeRawPayload;
        private boolean prettyPrint;

        private Builder() {
        }

        /** Writes {@code SearchMetadata.getRawPayload()} (Base64 body, headers); default {@code false}. */
        public Builder includeRawPayload(boolean includeRawPayload) {
            this.includeRawPayload = includeRawPayload;
            return this;
        }

        /** Indented output (two spaces); default {@code false}. */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public Serp4jJson build() {
            return new Serp4jJson(this);
        }
    }
}
