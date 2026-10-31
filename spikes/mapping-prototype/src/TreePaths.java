import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PROTOTYP (Spike), kein Produktivcode.
 *
 * Minimale Pfadsprache für Mapping-Definitionen. Bewusst KEIN vollständiges JSONPath (keine Filter, keine
 * Rekursion), damit serp4j-mapping ohne Fremdbibliothek auskommt:
 *
 * <pre>
 *   path    := ['$'] segment ('.' segment)*
 *   segment := name ('[' (index | '*') ']')*
 *   name    := [A-Za-z0-9_-]+        (Schlüssel mit Sonderzeichen: 'name' in einfachen Anführungszeichen)
 * </pre>
 *
 * {@code [*]} fächert in eine Liste auf. {@link #select} liefert deshalb immer eine Liste von Treffern
 * (leer, wenn der Pfad nicht existiert). Nichts darüber hinaus: Alles, was komplizierter ist, ist ein Fall
 * für einen Custom Mapper, nicht für eine mächtigere Pfadsprache.
 */
final class TreePaths {

    private TreePaths() {}

    static List<Object> select(Object root, String path) {
        List<Object> current = Collections.singletonList(root);
        String p = path.startsWith("$.") ? path.substring(2) : path.equals("$") ? "" : path;
        if (p.isEmpty()) return current;
        for (String segment : split(p)) {
            String name = segment;
            List<String> brackets = new ArrayList<>();
            int b = segment.indexOf('[');
            if (b >= 0) {
                name = segment.substring(0, b);
                String rest = segment.substring(b);
                while (!rest.isEmpty()) {
                    int close = rest.indexOf(']');
                    brackets.add(rest.substring(1, close));
                    rest = rest.substring(close + 1);
                }
            }
            if (name.startsWith("'") && name.endsWith("'")) name = name.substring(1, name.length() - 1);
            List<Object> next = new ArrayList<>();
            for (Object o : current) {
                Object v = child(o, name);
                if (v == null) continue;
                List<Object> stage = Collections.singletonList(v);
                for (String idx : brackets) {
                    List<Object> fan = new ArrayList<>();
                    for (Object candidate : stage) {
                        if (!(candidate instanceof List)) continue;
                        List<?> list = (List<?>) candidate;
                        if (idx.equals("*")) fan.addAll(list);
                        else {
                            int n = Integer.parseInt(idx);
                            if (n >= 0 && n < list.size()) fan.add(list.get(n));
                        }
                    }
                    stage = fan;
                }
                next.addAll(stage);
            }
            current = next;
        }
        return current;
    }

    /** Erster Treffer oder null; {@code Json.NULL} wird wie "nicht vorhanden" behandelt. */
    static Object first(Object root, String path) {
        List<Object> hits = select(root, path);
        if (hits.isEmpty()) return null;
        Object v = hits.get(0);
        return v == Json.NULL ? null : v;
    }

    private static Object child(Object o, String name) {
        if (name.isEmpty()) return o;
        if (o instanceof Map) return ((Map<?, ?>) o).get(name);
        return null;
    }

    private static List<String> split(String p) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quoted = false;
        for (char c : p.toCharArray()) {
            if (c == '\'') quoted = !quoted;
            if (c == '.' && !quoted) { out.add(b.toString()); b.setLength(0); }
            else b.append(c);
        }
        out.add(b.toString());
        return out;
    }
}
