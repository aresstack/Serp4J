import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * PROTOTYP (Spike), kein Produktivcode.
 *
 * Kern der zweistufigen Mapping-Infrastruktur (Vorschlag für serp4j-mapping):
 *
 * <ol>
 *   <li>{@link Definition}: deklarative Mapping-Definition, selbst aus einem neutralen Baum gelesen
 *       (im Zielsystem aus einer JSON-Ressource im Provider-Modul, z. B. {@code mapping/brave-web-v1.json}).</li>
 *   <li>{@link Engine}: wendet die Definition auf eine Provider-Antwort an. Deklarative Stufe zuerst,
 *       danach programmierbare {@link CustomMapper} je Sektion und je Diskriminator-Wert.</li>
 *   <li>{@link Converters} und {@link CustomMappers}: Registries, die Provider-Module und Serp4J-Nutzer
 *       mit eigenen Einträgen erweitern können.</li>
 * </ol>
 *
 * Die Engine kennt kein Gson und keine Provider. Sie arbeitet nur auf dem neutralen Baum (Map/List/…),
 * der im Zielsystem {@code StructuredValue} ist.
 */
final class Mapping {

    private Mapping() {}

    // ------------------------------------------------------------------ Definition (deklarativ)

    /** Eine Feldzuordnung: Ziel ← erste vorhandene Quelle aus {@code sources}, optional konvertiert. */
    static final class FieldMapping {
        final String target;
        final List<String> sources;
        final String converter;     // Name in Converters, null = Identität
        final Object defaultValue;  // null = kein Default
        final boolean required;

        FieldMapping(String target, List<String> sources, String converter, Object defaultValue, boolean required) {
            this.target = target; this.sources = sources; this.converter = converter;
            this.defaultValue = defaultValue; this.required = required;
        }
    }

    /** Eine Sektion: ein Pfad, der Elemente liefert, plus Feldzuordnungen und Regeln für den Rest. */
    static final class Section {
        final String id;
        final String kind;              // "items" | "features" | "elements" (polymorph: Diskriminator entscheidet item/feature)
        final String path;              // z. B. "$.organic_results[*]"
        final String resultType;        // Default-ResultType bzw. Feature-Typ, falls nicht per Diskriminator
        final String discriminator;     // Pfad innerhalb des Elements (z. B. "type"), null = kein Diskriminator
        final Map<String, String> discriminatorMap; // Diskriminator-Wert → resultType/featureType
        final List<String> skipWhen;    // Diskriminator-Werte, die bewusst übersprungen werden
        final String positionMode;      // "index" (Listenindex+1 → rankInGroup) | "fields" | "none"
        final List<FieldMapping> fields;
        /** Feldzuordnungen je aufgelöstem Typ-Code (z. B. "featured_snippet"); ergänzen/überschreiben {@link #fields}. */
        final Map<String, List<FieldMapping>> fieldsByType;
        final boolean keepUnmapped;

        Section(String id, String kind, String path, String resultType, String discriminator,
                Map<String, String> discriminatorMap, List<String> skipWhen, String positionMode,
                List<FieldMapping> fields, Map<String, List<FieldMapping>> fieldsByType, boolean keepUnmapped) {
            this.id = id; this.kind = kind; this.path = path; this.resultType = resultType;
            this.discriminator = discriminator; this.discriminatorMap = discriminatorMap; this.skipWhen = skipWhen;
            this.positionMode = positionMode; this.fields = fields; this.fieldsByType = fieldsByType;
            this.keepUnmapped = keepUnmapped;
        }

        /** Gemeinsame Felder plus typspezifische Felder (typspezifische gewinnen bei gleichem Ziel). */
        List<FieldMapping> fieldsFor(String typeCode) {
            List<FieldMapping> specific = typeCode == null ? null : fieldsByType.get(typeCode);
            if (specific == null) return fields;
            Map<String, FieldMapping> merged = new LinkedHashMap<>();
            for (FieldMapping fm : fields) merged.put(fm.target, fm);
            for (FieldMapping fm : specific) merged.put(fm.target, fm);
            return new ArrayList<>(merged.values());
        }
    }

    static final class Definition {
        final String id;
        final int version;
        final List<FieldMapping> metadata;
        final List<Section> sections;
        final boolean keepUnmappedRoot;

        Definition(String id, int version, List<FieldMapping> metadata, List<Section> sections, boolean keepUnmappedRoot) {
            this.id = id; this.version = version; this.metadata = metadata; this.sections = sections;
            this.keepUnmappedRoot = keepUnmappedRoot;
        }

        /** Liest die Definition aus einem neutralen Baum (im Zielsystem: StructuredValue aus einer Ressource). */
        @SuppressWarnings("unchecked")
        static Definition from(Object tree) {
            Map<String, Object> d = (Map<String, Object>) tree;
            List<Section> sections = new ArrayList<>();
            for (Object o : (List<Object>) d.getOrDefault("sections", Collections.emptyList())) {
                Map<String, Object> s = (Map<String, Object>) o;
                Map<String, String> discMap = new LinkedHashMap<>();
                Map<String, Object> dm = (Map<String, Object>) s.get("discriminatorMap");
                if (dm != null) for (Map.Entry<String, Object> e : dm.entrySet()) discMap.put(e.getKey(), (String) e.getValue());
                List<String> skip = new ArrayList<>();
                for (Object x : (List<Object>) s.getOrDefault("skipWhen", Collections.emptyList())) skip.add((String) x);
                sections.add(new Section(
                        (String) s.get("id"),
                        (String) s.getOrDefault("kind", "items"),
                        (String) s.get("path"),
                        (String) s.get("resultType"),
                        (String) s.get("discriminator"),
                        discMap, skip,
                        (String) s.getOrDefault("position", "fields"),
                        fields((Map<String, Object>) s.getOrDefault("fields", Collections.emptyMap())),
                        fieldsByType((Map<String, Object>) s.getOrDefault("fieldsByType", Collections.emptyMap())),
                        !Boolean.FALSE.equals(s.get("keepUnmapped"))));
            }
            return new Definition(
                    (String) d.get("id"),
                    ((Number) d.getOrDefault("version", 1L)).intValue(),
                    fields((Map<String, Object>) d.getOrDefault("metadata", Collections.emptyMap())),
                    sections,
                    !Boolean.FALSE.equals(d.get("keepUnmappedRoot")));
        }

        /**
         * Kurzform: "title": ["title", "name"]  oder  "title": "name"
         * Langform: "position": {"from": ["position", "rank"], "convert": "int", "default": 0, "required": true}
         */
        @SuppressWarnings("unchecked")
        private static Map<String, List<FieldMapping>> fieldsByType(Map<String, Object> raw) {
            Map<String, List<FieldMapping>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) out.put(e.getKey(), fields((Map<String, Object>) e.getValue()));
            return out;
        }

        @SuppressWarnings("unchecked")
        private static List<FieldMapping> fields(Map<String, Object> raw) {
            List<FieldMapping> out = new ArrayList<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                Object spec = e.getValue();
                List<String> sources = new ArrayList<>();
                String converter = null; Object def = null; boolean required = false;
                if (spec instanceof String) sources.add((String) spec);
                else if (spec instanceof List) for (Object x : (List<Object>) spec) sources.add((String) x);
                else {
                    Map<String, Object> m = (Map<String, Object>) spec;
                    Object from = m.get("from");
                    if (from instanceof String) sources.add((String) from);
                    else if (from != null) for (Object x : (List<Object>) from) sources.add((String) x);
                    converter = (String) m.get("convert");
                    def = m.get("default");
                    required = Boolean.TRUE.equals(m.get("required"));
                }
                out.add(new FieldMapping(e.getKey(), sources, converter, def, required));
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ Converters (Registry)

    static final class Converters {
        private final Map<String, Function<Object, Object>> byName = new HashMap<>();

        Converters() {
            register("string", v -> v == null ? null : String.valueOf(v));
            register("int", v -> {
                if (v == null) return null;
                if (v instanceof Number) return ((Number) v).intValue();
                String s = String.valueOf(v).trim();
                return s.isEmpty() ? null : Integer.valueOf(new BigDecimal(s).intValue());
            });
            register("stripHtml", v -> v == null ? null : String.valueOf(v).replaceAll("<[^>]+>", ""));
            register("host", v -> {
                if (v == null) return null;
                String s = String.valueOf(v);
                int p = s.indexOf("://");
                if (p >= 0) s = s.substring(p + 3);
                int slash = s.indexOf('/');
                if (slash >= 0) s = s.substring(0, slash);
                return s.toLowerCase();
            });
        }

        void register(String name, Function<Object, Object> fn) { byName.put(name, fn); }

        Object apply(String name, Object value) {
            if (name == null) return value;
            Function<Object, Object> fn = byName.get(name);
            if (fn == null) throw new IllegalArgumentException("unknown converter: " + name);
            return fn.apply(value);
        }
    }

    // ------------------------------------------------------------------ Custom Mapper (programmierbar)

    /** Kontext eines Mapping-Laufs: Provider-Namespace, Warnungen, ID-Vergabe, Zugriff auf Konverter. */
    static final class Context {
        final String namespace;
        final Converters converters;
        final List<String> warnings = new ArrayList<>();
        private int nextId = 1;

        Context(String namespace, Converters converters) { this.namespace = namespace; this.converters = converters; }

        String nextElementId() { return "e" + (nextId++); }
        void warn(String message) { warnings.add(message); }
    }

    /** Programmierbarer Mapper für ein Item: erhält das Rohelement, den vorbelegten Builder und den Kontext. */
    interface ItemMapper {
        /** @param consumed Pfade, die der Custom Mapper gelesen hat (für die Unmapped-Berechnung). */
        void map(Map<String, Object> element, Model.Item item, Context ctx, Set<String> consumed);
    }

    /** Programmierbarer Mapper für ein Feature; liefert null, wenn er das Element nicht übernimmt. */
    interface FeatureMapper {
        Model.Feature map(Map<String, Object> element, Context ctx, Set<String> consumed);
    }

    static final class CustomMappers {
        private final Map<String, ItemMapper> itemBySection = new HashMap<>();
        private final Map<String, FeatureMapper> featureBySectionAndType = new HashMap<>();

        /**
         * Registrierung ist bewusst an die Sektion gebunden, nicht global an den Typ-Code: Ein Mapper für
         * DataForSEOs {@code knowledge_graph} darf nie auf Braves {@code infobox} angewendet werden, auch wenn beide
         * auf {@code knowledge_panel} abbilden. (Erkenntnis aus dem ersten Prototyp-Lauf, siehe README.)
         */
        CustomMappers itemsOf(String sectionId, ItemMapper m) { itemBySection.put(sectionId, m); return this; }
        CustomMappers featureOf(String sectionId, String typeCode, FeatureMapper m) {
            featureBySectionAndType.put(sectionId + "#" + typeCode, m); return this;
        }

        ItemMapper forSection(String sectionId) { return itemBySection.get(sectionId); }
        FeatureMapper forFeature(String sectionId, String typeCode) { return featureBySectionAndType.get(sectionId + "#" + typeCode); }
    }

    // ------------------------------------------------------------------ Engine

    static final class Engine {
        private final Converters converters;
        private final CustomMappers custom;

        Engine(Converters converters, CustomMappers custom) { this.converters = converters; this.custom = custom; }

        @SuppressWarnings("unchecked")
        Model.Result map(Object root, Definition def, String namespace) {
            Context ctx = new Context(namespace, converters);
            Model.Result result = new Model.Result();
            Set<String> consumedRoot = new LinkedHashSet<>();

            // 1. Metadaten (Top-Level)
            for (FieldMapping fm : def.metadata) {
                Object v = resolve(root, fm, ctx, consumedRoot, "metadata");
                if (v != null) result.metadata.put(fm.target, v);
            }

            // 2. Sektionen
            for (Section section : def.sections) {
                consumedRoot.add(rootKey(section.path));
                List<Object> elements = TreePaths.select(root, section.path);
                int index = 0;
                for (Object raw : elements) {
                    index++;
                    if (!(raw instanceof Map)) { ctx.warn(section.id + ": element " + index + " is not an object"); continue; }
                    Map<String, Object> element = (Map<String, Object>) raw;
                    Set<String> consumed = new LinkedHashSet<>();

                    // Diskriminator → konkreter Typ
                    String typeCode = section.resultType;
                    if (section.discriminator != null) {
                        Object disc = TreePaths.first(element, section.discriminator);
                        consumed.add(rootKey(section.discriminator));
                        String discValue = disc == null ? null : String.valueOf(disc);
                        if (discValue != null && section.skipWhen.contains(discValue)) continue;
                        String mapped = discValue == null ? null : section.discriminatorMap.get(discValue);
                        typeCode = mapped != null ? mapped : discValue != null ? discValue : section.resultType;
                    }

                    // Polymorphe Sektion: Diskriminator-Map-Werte "item:<ResultType>" oder "feature:<FeatureType>"
                    String kind = section.kind;
                    if (kind.equals("elements")) {
                        if (typeCode != null && typeCode.startsWith("item:")) { kind = "items"; typeCode = typeCode.substring(5); }
                        else if (typeCode != null && typeCode.startsWith("feature:")) { kind = "features"; typeCode = typeCode.substring(8); }
                        else {
                            result.features.add(genericFeature(element, typeCode, ctx, Collections.<String>emptySet()));
                            ctx.warn(section.id + ": unknown element type '" + typeCode + "' kept as GenericSerpFeature");
                            continue;
                        }
                    }

                    if (kind.equals("items")) {
                        boolean known = section.discriminator == null || section.kind.equals("elements")
                                || section.discriminatorMap.containsKey(String.valueOf(TreePaths.first(element, section.discriminator)));
                        if (!known) {
                            // Unbekannter Typ in einer Item-Sektion → als GenericSerpFeature erhalten, nie verwerfen.
                            result.features.add(genericFeature(element, typeCode, ctx, Collections.<String>emptySet()));
                            ctx.warn(section.id + ": unknown element type '" + typeCode + "' kept as GenericSerpFeature");
                            continue;
                        }
                        Model.Item item = new Model.Item();
                        item.id = ctx.nextElementId();
                        item.type = typeCode;
                        if (section.positionMode.equals("index")) item.rankInGroup = index;
                        for (FieldMapping fm : section.fieldsFor(typeCode)) assign(item, fm, resolve(element, fm, ctx, consumed, section.id));
                        ItemMapper im = custom.forSection(section.id);
                        if (im != null) im.map(element, item, ctx, consumed);
                        if (item.url == null) { ctx.warn(section.id + ": element " + index + " without url skipped"); continue; }
                        if (section.keepUnmapped) item.unmapped = unmapped(element, consumed);
                        result.items.add(item);
                    } else {
                        FeatureMapper fmapper = custom.forFeature(section.id, typeCode);
                        Model.Feature feature = null;
                        if (fmapper != null) {
                            feature = fmapper.map(element, ctx, consumed);
                            if (feature != null) {
                                feature.id = ctx.nextElementId();
                                feature.type = typeCode;
                                if (section.keepUnmapped) feature.unmapped = unmapped(element, consumed);
                            }
                        }
                        if (feature == null) {
                            // Deklarative Felder für Features (flach) oder Generic-Fallback
                            feature = new Model.Feature();
                            feature.id = ctx.nextElementId();
                            feature.type = typeCode;
                            List<FieldMapping> fields = section.fieldsFor(typeCode);
                            for (FieldMapping fm : fields) {
                                Object v = resolve(element, fm, ctx, consumed, section.id);
                                if (v != null) feature.fields.put(fm.target, v);
                            }
                            feature.generic = fields.isEmpty() || feature.fields.isEmpty();
                            if (feature.generic) {
                                feature.fields.clear();
                                feature.unmapped = unmapped(element, Collections.<String>emptySet());
                            } else if (section.keepUnmapped) {
                                feature.unmapped = unmapped(element, consumed);
                            }
                        }
                        result.features.add(feature);
                    }
                }
            }

            // 3. Rest der Wurzel
            if (def.keepUnmappedRoot && root instanceof Map) result.unmappedRoot = unmapped((Map<String, Object>) root, consumedRoot);
            result.warnings.addAll(ctx.warnings);
            return result;
        }

        private Object resolve(Object scope, FieldMapping fm, Context ctx, Set<String> consumed, String where) {
            Object value = null;
            for (String src : fm.sources) {
                Object v = TreePaths.first(scope, src);
                if (v != null) { value = v; consumed.add(rootKey(src)); break; }
            }
            if (value == null) value = fm.defaultValue;
            if (value == null && fm.required) ctx.warn(where + ": required field '" + fm.target + "' missing");
            return value == null ? null : ctx.converters.apply(fm.converter, value);
        }

        private static void assign(Model.Item item, FieldMapping fm, Object v) {
            if (v == null) return;
            switch (fm.target) {
                case "url": item.url = (String) v; break;
                case "title": item.title = (String) v; break;
                case "snippet": item.snippet = (String) v; break;
                case "domain": item.domain = (String) v; break;
                case "displayUrl": item.displayUrl = (String) v; break;
                case "rankInGroup": item.rankInGroup = (Integer) v; break;
                case "rankAbsolute": item.rankAbsolute = (Integer) v; break;
                case "area": item.area = (String) v; break;
                case "additionalSnippets":
                    if (v instanceof List) for (Object o : (List<?>) v) item.additionalSnippets.add(String.valueOf(o));
                    else item.additionalSnippets.add(String.valueOf(v));
                    break;
                default:
                    // Unbekanntes Ziel = typisierte Extension unter dem Provider-Namespace (z. B. "brave:page_age")
                    item.extensions.put(fm.target, v);
            }
        }

        private static Model.Feature genericFeature(Map<String, Object> element, String type, Context ctx, Set<String> consumed) {
            Model.Feature f = new Model.Feature();
            f.id = ctx.nextElementId();
            f.type = type;
            f.generic = true;
            f.unmapped = unmapped(element, consumed);
            return f;
        }

        /** Alle Top-Level-Schlüssel des Elements, die kein Mapping gelesen hat. */
        private static Map<String, Object> unmapped(Map<String, Object> element, Set<String> consumed) {
            Map<String, Object> rest = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : element.entrySet()) if (!consumed.contains(e.getKey())) rest.put(e.getKey(), e.getValue());
            return rest;
        }

        /** Erster Pfadabschnitt ohne '$.' und ohne Index, als Schlüssel für die Unmapped-Berechnung. */
        private static String rootKey(String path) {
            String p = path.startsWith("$.") ? path.substring(2) : path;
            int dot = p.indexOf('.');
            if (dot >= 0) p = p.substring(0, dot);
            int br = p.indexOf('[');
            if (br >= 0) p = p.substring(0, br);
            return p.replace("'", "");
        }
    }
}
