import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROTOTYP (Spike), kein Produktivcode.
 *
 * Wendet drei Mapping-Definitionen auf drei unterschiedlich strukturierte Provider-Antworten an:
 *
 * <ul>
 *   <li>Provider A/B: Angelos Beispiel (gleiche Information, andere Feldnamen) – rein deklarativ.</li>
 *   <li>Provider C: DataForSEO-artige flache, polymorphe {@code items[]}-Liste mit {@code type}-Diskriminator,
 *       {@code rank_group}/{@code rank_absolute}, Knowledge Graph, PAA, Werbung und einem unbekannten Typ –
 *       deklarativ plus Custom Mapper.</li>
 * </ul>
 *
 * Aufruf: {@code ./run.sh} (kompiliert mit {@code --release 8}).
 */
public final class Demo {

    public static void main(String[] args) throws IOException {
        String base = args.length > 0 ? args[0] : ".";
        Mapping.Converters converters = new Mapping.Converters();

        // Provider-/nutzerspezifische Erweiterungen der Registries – so würde ein Provider-Modul oder ein
        // Serp4J-Nutzer eigene Konverter und Mapper beisteuern, ohne die Engine anzufassen.
        converters.register("serpArea", v -> v == null ? null : "right".equals(v) ? "SIDEBAR" : "MAIN");

        // Registry je Definition (ADR-019): Provider A und B haben keine Custom Mapper, C hat drei. Eine über alle
        // Definitionen geteilte Instanz ist ausgeschlossen, auch wenn sie dieselben Sektions-/Typ-IDs verwenden.
        Map<String, Mapping.CustomMappers> customByDefinition = new LinkedHashMap<>();
        customByDefinition.put("provider-a", new Mapping.CustomMappers());
        customByDefinition.put("provider-b", new Mapping.CustomMappers());
        customByDefinition.put("provider-c", new Mapping.CustomMappers()
                // C: Rangsemantik + Spalte sind mehr als Feldnamen → Custom Item Mapper ergänzt den deklarativen Teil.
                .itemsOf("serp", (element, item, ctx, consumed) -> {
                    Object pos = element.get("position");
                    consumed.add("position");
                    Object area = ctx.converters.apply("serpArea", pos);
                    if (area != null) item.area = (String) area;
                    if (Boolean.TRUE.equals(element.get("is_featured_snippet"))) {
                        item.extensions.put(ctx.namespace + ":is_featured_snippet", Boolean.TRUE);
                    }
                    consumed.add("is_featured_snippet");
                })
                // C: Knowledge Graph ist verschachtelt → Custom Feature Mapper.
                .featureOf("serp", "knowledge_panel", (element, ctx, consumed) -> {
                    Model.Feature f = new Model.Feature();
                    f.fields.put("title", element.get("title"));
                    f.fields.put("description", element.get("description"));
                    List<Map<String, Object>> facts = new ArrayList<>();
                    for (Object row : TreePaths.select(element, "items[*]")) {
                        Map<?, ?> r = (Map<?, ?>) row;
                        if (!"knowledge_graph_row_item".equals(r.get("type"))) continue;
                        Map<String, Object> fact = new LinkedHashMap<>();
                        fact.put("key", r.get("title"));
                        fact.put("value", r.get("text"));
                        facts.add(fact);
                    }
                    f.fields.put("facts", facts);
                    consumed.add("title"); consumed.add("description"); consumed.add("items"); consumed.add("type");
                    return f;
                })
                // C: People also ask → Fragen + Antwortquelle aus verschachtelten expanded_element.
                .featureOf("serp", "people_also_ask", (element, ctx, consumed) -> {
                    Model.Feature f = new Model.Feature();
                    List<Map<String, Object>> qas = new ArrayList<>();
                    for (Object q : TreePaths.select(element, "items[*]")) {
                        Map<String, Object> qa = new LinkedHashMap<>();
                        qa.put("question", TreePaths.first(q, "title"));
                        qa.put("answer", TreePaths.first(q, "expanded_element[0].description"));
                        qa.put("sourceUrl", TreePaths.first(q, "expanded_element[0].url"));
                        qas.add(qa);
                    }
                    f.fields.put("questions", qas);
                    consumed.add("items"); consumed.add("type");
                    return f;
                }));

        for (String p : new String[] {"provider-a", "provider-b", "provider-c"}) {
            Object input = Json.parse(read(base + "/inputs/" + p + ".json"));
            Mapping.Definition def = Mapping.Definition.from(Json.parse(read(base + "/definitions/" + p + ".json")));
            Mapping.Engine engine = new Mapping.Engine(converters, customByDefinition.get(p));
            Model.Result r = engine.map(input, def, p);
            System.out.println("=== " + def.id + " (v" + def.version + ") ===");
            System.out.print(r);
            System.out.println();
        }
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
