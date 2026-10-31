import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROTOTYP (Spike), kein Produktivcode.
 *
 * Minimaler JSON-Reader, der einen neutralen Wertebaum erzeugt: Map (LinkedHashMap), List, String, Number
 * (BigDecimal/Long), Boolean, null. Er steht im Spike stellvertretend für {@code StructuredValue} aus
 * serp4j-core (ADR-006). In der Zielarchitektur liefert serp4j-json diesen Baum per Gson; die Mapping-Engine
 * sieht nur den Baum, nie Gson.
 */
final class Json {

    static final Object NULL = new Object() {
        @Override public String toString() { return "null"; }
    };

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("trailing data");
        return v;
    }

    private Object value() {
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') { expect("true"); return Boolean.TRUE; }
        if (c == 'f') { expect("false"); return Boolean.FALSE; }
        if (c == 'n') { expect("null"); return NULL; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws(); if (s.charAt(i++) != ':') throw err("':' expected");
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("',' or '}' expected");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("',' or ']' expected");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("string expected");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(start, i);
        if (n.isEmpty()) throw err("value expected");
        if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.parseLong(n);
        return new java.math.BigDecimal(n);
    }

    private void expect(String lit) {
        if (!s.startsWith(lit, i)) throw err(lit + " expected");
        i += lit.length();
    }

    private char peek() {
        if (i >= s.length()) throw err("unexpected end");
        return s.charAt(i);
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON: " + msg + " at " + i);
    }

    /** Kompakte Ausgabe für den Demo-Report. */
    static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder b) {
        if (v == null || v == NULL) { b.append("null"); return; }
        if (v instanceof String) { b.append('"').append(((String) v).replace("\"", "\\\"")).append('"'); return; }
        if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(e.getKey()).append("\":");
                write(e.getValue(), b);
            }
            b.append('}');
            return;
        }
        if (v instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) b.append(',');
                first = false;
                write(o, b);
            }
            b.append(']');
            return;
        }
        b.append(v);
    }
}
