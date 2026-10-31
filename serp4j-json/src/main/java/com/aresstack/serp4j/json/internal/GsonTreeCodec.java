package com.aresstack.serp4j.json.internal;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.JsonTreeException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming conversion with Gson's {@link JsonReader}/{@link JsonWriter}; numbers are read from their literal text,
 * so no precision is lost.
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class GsonTreeCodec {

    private static final int MAX_DEPTH = 512;

    private GsonTreeCodec() {
    }

    public static StructuredValue read(String json) {
        try {
            return read(new StringReader(json));
        } catch (IOException e) {
            throw new JsonTreeException("invalid JSON: " + e.getMessage(), e);
        }
    }

    public static StructuredValue read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        // Gson's default is "legacy strict", which still accepts a few non-standard constructs; reject them.
        reader.setStrictness(Strictness.STRICT);
        try {
            StructuredValue value = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonTreeException("invalid JSON: trailing data at " + reader.getPath());
            }
            return value;
        } catch (MalformedJsonException | IllegalStateException | NumberFormatException e) {
            throw new JsonTreeException("invalid JSON: " + e.getMessage(), e);
        } catch (java.io.EOFException e) {
            throw new JsonTreeException("invalid JSON: unexpected end of input", e);
        }
    }

    private static StructuredValue readValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new JsonTreeException("invalid JSON: nesting deeper than " + MAX_DEPTH + " at " + reader.getPath());
        }
        JsonToken token = reader.peek();
        switch (token) {
            case BEGIN_OBJECT:
                Map<String, StructuredValue> members = new LinkedHashMap<String, StructuredValue>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    members.put(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                return StructuredValue.ofObject(members);
            case BEGIN_ARRAY:
                List<StructuredValue> elements = new ArrayList<StructuredValue>();
                reader.beginArray();
                while (reader.hasNext()) {
                    elements.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                return StructuredValue.ofArray(elements);
            case STRING:
                return StructuredValue.of(reader.nextString());
            case NUMBER:
                return StructuredValue.of(new BigDecimal(reader.nextString()));
            case BOOLEAN:
                return StructuredValue.of(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return StructuredValue.nullValue();
            default:
                throw new JsonTreeException("invalid JSON: unexpected " + token + " at " + reader.getPath());
        }
    }

    public static String write(StructuredValue value, boolean pretty) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            if (pretty) {
                writer.setIndent("  ");
            }
            writer.setHtmlSafe(false);
            writer.setSerializeNulls(true);
            writeValue(writer, value);
        } catch (IOException e) {
            throw new IllegalStateException("writing to a StringWriter failed", e);
        }
        return out.toString();
    }

    private static void writeValue(JsonWriter writer, StructuredValue value) throws IOException {
        switch (value.getKind()) {
            case OBJECT:
                writer.beginObject();
                for (Map.Entry<String, StructuredValue> e : value.getMembers().entrySet()) {
                    writer.name(e.getKey());
                    writeValue(writer, e.getValue());
                }
                writer.endObject();
                break;
            case ARRAY:
                writer.beginArray();
                for (StructuredValue e : value.getElements()) {
                    writeValue(writer, e);
                }
                writer.endArray();
                break;
            case STRING:
                writer.value(value.asText().get());
                break;
            case NUMBER:
                writer.value(value.asNumber().get());
                break;
            case BOOLEAN:
                writer.value(value.asBoolean().get());
                break;
            default:
                writer.nullValue();
        }
    }
}
