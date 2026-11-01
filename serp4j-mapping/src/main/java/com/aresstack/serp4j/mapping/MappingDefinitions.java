package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.mapping.internal.Targets;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and validates {@link MappingDefinition}s from a {@link StructuredValue} tree (ADR-019 §2). The tree is
 * usually a JSON resource read with {@code JsonTrees} from {@code serp4j-json}:
 *
 * <pre>{@code
 * MappingDefinition definition = MappingDefinitions.from(JsonTrees.readResource(MyProvider.class, "mapping/my-web-v1.json"));
 * }</pre>
 *
 * <p>Validation is strict, so a typo in a definition fails when it is loaded and not as silently missing data:
 * unknown keys, unknown targets, targets not supported by the element type, invalid paths and codes, duplicate
 * section ids and position fields in sections without positions are all rejected. All problems are collected into
 * one {@link InvalidMappingDefinitionException}. Converter names are checked when the definition is bound to an
 * engine ({@link MappingEngine.Builder#build()}), because converters belong to the engine.</p>
 */
public final class MappingDefinitions {

    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Pattern CONVERTER = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Set<String> ROOT_KEYS = keys("id", "version", "namespace", "description", "metadata",
            "sections", "keepUnmappedRoot");
    private static final Set<String> SECTION_KEYS = keys("id", "description", "kind", "path", "type",
            "discriminator", "discriminatorMap", "skipWhen", "position", "fields", "fieldsByType", "keepUnmapped");
    private static final Set<String> FIELD_KEYS = keys("from", "convert", "default", "required");

    private MappingDefinitions() {
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }

    /**
     * Reads and validates a definition.
     *
     * @throws InvalidMappingDefinitionException if the definition is invalid
     */
    public static MappingDefinition from(StructuredValue tree) {
        Objects.requireNonNull(tree, "tree");
        return new Reader(tree).read();
    }

    /** Collects problems while reading; each problem carries its location in the definition. */
    private static final class Reader {
        private final StructuredValue tree;
        private final List<String> problems = new ArrayList<String>();
        private String namespace;

        Reader(StructuredValue tree) {
            this.tree = tree;
        }

        MappingDefinition read() {
            if (!tree.isObject()) {
                throw new InvalidMappingDefinitionException("?", Collections.singletonList("definition must be an object"));
            }
            checkKeys(tree, ROOT_KEYS, "");
            String id = code(tree, "id", "id", true);
            int version = version(tree);
            namespace = code(tree, "namespace", "namespace", true);
            String description = text(tree, "description", "description");

            List<FieldMapping> metadata = Collections.emptyList();
            StructuredValue meta = tree.get("metadata").orElse(null);
            if (meta != null) {
                metadata = fields(meta, "metadata", FieldScope.METADATA, null);
            }

            List<SectionDefinition> sections = new ArrayList<SectionDefinition>();
            StructuredValue rawSections = tree.get("sections").orElse(null);
            if (rawSections != null) {
                if (!rawSections.isArray()) {
                    problems.add("sections: must be an array");
                } else {
                    Set<String> ids = new HashSet<String>();
                    List<StructuredValue> elements = rawSections.getElements();
                    for (int i = 0; i < elements.size(); i++) {
                        SectionDefinition section = section(elements.get(i), "sections[" + i + "]");
                        if (section != null) {
                            if (!ids.add(section.getId())) {
                                problems.add("sections[" + i + "].id: duplicate section id '" + section.getId() + "'");
                            }
                            sections.add(section);
                        }
                    }
                }
            }
            boolean keepRoot = bool(tree, "keepUnmappedRoot", "keepUnmappedRoot", true);
            if (!problems.isEmpty()) {
                throw new InvalidMappingDefinitionException(id != null ? id : "?", problems);
            }
            return new MappingDefinition(id, version, namespace, description, metadata,
                    Collections.unmodifiableList(sections), keepRoot);
        }

        // ------------------------------------------------------------ sections

        private SectionDefinition section(StructuredValue s, String where) {
            if (!s.isObject()) {
                problems.add(where + ": must be an object");
                return null;
            }
            int before = problems.size();
            checkKeys(s, SECTION_KEYS, where + ".");
            String id = code(s, "id", where + ".id", true);
            String at = id != null ? "section '" + id + "'" : where;

            SectionKind kind = SectionKind.ITEMS;
            String rawKind = text(s, "kind", at + ".kind");
            if (rawKind != null) {
                try {
                    kind = SectionKind.valueOf(rawKind.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    problems.add(at + ".kind: must be items, features or elements but was '" + rawKind + "'");
                }
            }
            TreePath path = path(s, "path", at + ".path", true);
            TreePath discriminator = path(s, "discriminator", at + ".discriminator", false);

            ElementType defaultType = null;
            String rawType = text(s, "type", at + ".type");
            if (rawType != null) {
                defaultType = elementType(rawType, kind, at + ".type");
            }

            Map<String, ElementType> discriminatorMap = new LinkedHashMap<String, ElementType>();
            StructuredValue rawMap = s.get("discriminatorMap").orElse(null);
            if (rawMap != null) {
                if (!rawMap.isObject()) {
                    problems.add(at + ".discriminatorMap: must be an object");
                } else {
                    for (Map.Entry<String, StructuredValue> e : rawMap.getMembers().entrySet()) {
                        String w = at + ".discriminatorMap." + e.getKey();
                        if (!e.getValue().isString()) {
                            problems.add(w + ": must be a string");
                            continue;
                        }
                        ElementType t = elementType(e.getValue().asText().get(), kind, w);
                        if (t != null) {
                            discriminatorMap.put(e.getKey(), t);
                        }
                    }
                }
                if (discriminator == null) {
                    problems.add(at + ": discriminatorMap requires a discriminator");
                }
            }

            Set<String> skipWhen = new LinkedHashSet<String>();
            StructuredValue rawSkip = s.get("skipWhen").orElse(null);
            if (rawSkip != null) {
                for (String v : stringList(rawSkip, at + ".skipWhen")) {
                    skipWhen.add(v);
                    if (discriminatorMap.containsKey(v)) {
                        problems.add(at + ".skipWhen: '" + v + "' is also mapped in discriminatorMap");
                    }
                }
                if (discriminator == null) {
                    problems.add(at + ": skipWhen requires a discriminator");
                }
            }

            if (kind == SectionKind.ELEMENTS && discriminator == null) {
                problems.add(at + ": kind elements requires a discriminator");
            }
            if (defaultType == null && discriminatorMap.isEmpty()) {
                problems.add(at + ": needs a type or a discriminatorMap");
            }

            PositionMode position = PositionMode.FIELDS;
            String rawPosition = text(s, "position", at + ".position");
            if (rawPosition != null) {
                try {
                    position = PositionMode.valueOf(rawPosition.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    problems.add(at + ".position: must be index, fields or none but was '" + rawPosition + "'");
                }
            }

            Set<ElementType> types = new LinkedHashSet<ElementType>();
            if (defaultType != null) {
                types.add(defaultType);
            }
            types.addAll(discriminatorMap.values());

            List<FieldMapping> fields = Collections.emptyList();
            StructuredValue rawFields = s.get("fields").orElse(null);
            if (rawFields != null) {
                fields = fields(rawFields, at + ".fields", FieldScope.ELEMENT, null);
                for (FieldMapping f : fields) {
                    if (!supportedByAny(types, f.getTarget())) {
                        problems.add(at + ".fields." + f.getTarget() + ": target not supported by any type of the section "
                                + types);
                    }
                }
            }

            Map<String, List<FieldMapping>> fieldsByType = new LinkedHashMap<String, List<FieldMapping>>();
            StructuredValue rawByType = s.get("fieldsByType").orElse(null);
            if (rawByType != null) {
                if (!rawByType.isObject()) {
                    problems.add(at + ".fieldsByType: must be an object");
                } else {
                    for (Map.Entry<String, StructuredValue> e : rawByType.getMembers().entrySet()) {
                        String w = at + ".fieldsByType." + e.getKey();
                        List<ElementType> matching = new ArrayList<ElementType>();
                        for (ElementType t : types) {
                            if (t.getCode().equals(e.getKey())) {
                                matching.add(t);
                            }
                        }
                        if (matching.isEmpty()) {
                            problems.add(w + ": the section produces no type '" + e.getKey() + "' " + types);
                            continue;
                        }
                        if (matching.size() > 1) {
                            problems.add(w + ": ambiguous, '" + e.getKey() + "' is both an item and a feature type");
                            continue;
                        }
                        fieldsByType.put(e.getKey(), fields(e.getValue(), w, FieldScope.ELEMENT, matching.get(0)));
                    }
                }
            }

            if (position == PositionMode.NONE) {
                for (FieldMapping f : fields) {
                    if (Targets.isPosition(f.getTarget())) {
                        problems.add(at + ".fields." + f.getTarget() + ": position fields are not allowed with position none");
                    }
                }
                for (Map.Entry<String, List<FieldMapping>> e : fieldsByType.entrySet()) {
                    for (FieldMapping f : e.getValue()) {
                        if (Targets.isPosition(f.getTarget())) {
                            problems.add(at + ".fieldsByType." + e.getKey() + "." + f.getTarget()
                                    + ": position fields are not allowed with position none");
                        }
                    }
                }
            }

            boolean keepUnmapped = bool(s, "keepUnmapped", at + ".keepUnmapped", true);
            if (problems.size() > before || id == null || path == null) {
                return null;
            }
            Map<String, List<FieldMapping>> byType = new LinkedHashMap<String, List<FieldMapping>>(fieldsByType);
            return new SectionDefinition(id, kind, path, defaultType, discriminator,
                    Collections.unmodifiableMap(discriminatorMap), Collections.unmodifiableSet(skipWhen), position,
                    fields, Collections.unmodifiableMap(byType), keepUnmapped);
        }

        private ElementType elementType(String raw, SectionKind kind, String where) {
            String value = raw.trim();
            String prefix = null;
            String code = value;
            int colon = value.indexOf(':');
            if (colon >= 0) {
                prefix = value.substring(0, colon);
                code = value.substring(colon + 1);
            }
            boolean item;
            if (kind == SectionKind.ELEMENTS) {
                if ("item".equals(prefix)) {
                    item = true;
                } else if ("feature".equals(prefix)) {
                    item = false;
                } else {
                    problems.add(where + ": in a section of kind elements the type must be 'item:<code>' or "
                            + "'feature:<code>' but was '" + raw + "'");
                    return null;
                }
            } else {
                if (prefix != null) {
                    problems.add(where + ": prefix '" + prefix + ":' is only allowed in sections of kind elements");
                    return null;
                }
                item = kind == SectionKind.ITEMS;
            }
            try {
                return item ? ElementType.item(ResultType.of(code)) : ElementType.feature(SerpFeatureType.of(code));
            } catch (IllegalArgumentException e) {
                problems.add(where + ": " + e.getMessage());
                return null;
            }
        }

        private boolean supportedByAny(Set<ElementType> types, String target) {
            for (ElementType t : types) {
                if (kindFor(t, target) != null) {
                    return true;
                }
            }
            return types.isEmpty();
        }

        // ------------------------------------------------------------ fields

        private List<FieldMapping> fields(StructuredValue raw, String where, FieldScope scope, ElementType type) {
            if (!raw.isObject()) {
                problems.add(where + ": must be an object");
                return Collections.emptyList();
            }
            List<FieldMapping> out = new ArrayList<FieldMapping>();
            for (Map.Entry<String, StructuredValue> e : raw.getMembers().entrySet()) {
                String target = e.getKey();
                String w = where + "." + target;
                if (!checkTarget(target, scope, type, w)) {
                    continue;
                }
                FieldMapping f = field(target, e.getValue(), w);
                if (f != null) {
                    out.add(f);
                }
            }
            return Collections.unmodifiableList(out);
        }

        private boolean checkTarget(String target, FieldScope scope, ElementType type, String where) {
            if (Targets.isExtension(target)) {
                int colon = target.indexOf(':');
                String ns = target.substring(0, colon);
                String name = target.substring(colon + 1);
                if (!ns.equals(namespace)) {
                    problems.add(where + ": extension namespace must be the definition namespace '" + namespace
                            + "' but was '" + ns + "'");
                    return false;
                }
                if (!Targets.EXTENSION_NAME.matcher(name).matches()) {
                    problems.add(where + ": extension name must match " + Targets.EXTENSION_NAME.pattern());
                    return false;
                }
                return true;
            }
            if (scope == FieldScope.METADATA) {
                if (Targets.forMetadata(target) == null) {
                    problems.add(where + ": unknown metadata target (known: " + join(Targets.metadataTargets())
                            + ", or '" + namespace + ":<name>')");
                    return false;
                }
                return true;
            }
            if (type != null && kindFor(type, target) == null) {
                problems.add(where + ": target not supported for " + type + " (supported: " + supported(type) + ")");
                return false;
            }
            if (type == null && Targets.forItem(target) == null && !isAnyFeatureTarget(target)) {
                problems.add(where + ": unknown target");
                return false;
            }
            return true;
        }

        private boolean isAnyFeatureTarget(String target) {
            for (String code : Arrays.asList("knowledge_panel", "featured_snippet", "answer_box", "related_searches",
                    "generic")) {
                if (Targets.forFeature(code, target) != null) {
                    return true;
                }
            }
            return false;
        }

        private String supported(ElementType type) {
            List<String> names = new ArrayList<String>();
            if (type.isItem()) {
                for (String t : Targets.itemTargets()) {
                    names.add(t);
                }
            } else {
                for (String t : Targets.featureTargets(type.getCode())) {
                    names.add(t);
                }
            }
            for (String t : Targets.positionTargets()) {
                names.add(t);
            }
            names.add(namespace + ":<name>");
            return join(names);
        }

        private FieldMapping field(String target, StructuredValue spec, String where) {
            List<TreePath> sources = new ArrayList<TreePath>();
            String converter = null;
            StructuredValue defaultValue = null;
            boolean required = false;
            if (spec.isString() || spec.isArray()) {
                for (String p : stringList(spec, where)) {
                    TreePath path = parsePath(p, where);
                    if (path != null) {
                        sources.add(path);
                    }
                }
            } else if (spec.isObject()) {
                checkKeys(spec, FIELD_KEYS, where + ".");
                StructuredValue from = spec.get("from").orElse(null);
                if (from != null) {
                    for (String p : stringList(from, where + ".from")) {
                        TreePath path = parsePath(p, where + ".from");
                        if (path != null) {
                            sources.add(path);
                        }
                    }
                }
                converter = text(spec, "convert", where + ".convert");
                if (converter != null && !CONVERTER.matcher(converter).matches()) {
                    problems.add(where + ".convert: invalid converter name '" + converter + "'");
                }
                defaultValue = spec.get("default").orElse(null);
                if (defaultValue != null && defaultValue.isNull()) {
                    defaultValue = null;
                }
                required = bool(spec, "required", where + ".required", false);
                if (from == null && defaultValue == null) {
                    problems.add(where + ": needs 'from' or 'default'");
                }
            } else {
                problems.add(where + ": must be a path, a list of paths or an object");
                return null;
            }
            if (sources.isEmpty() && defaultValue == null) {
                if (!spec.isObject()) {
                    problems.add(where + ": needs at least one source path");
                }
                return null;
            }
            return new FieldMapping(target, Collections.unmodifiableList(sources), converter, defaultValue, required);
        }

        private Targets.Kind kindFor(ElementType type, String target) {
            return type.isItem() ? Targets.forItem(target) : Targets.forFeature(type.getCode(), target);
        }

        // ------------------------------------------------------------ primitives

        private void checkKeys(StructuredValue object, Set<String> allowed, String prefix) {
            for (String key : object.getMembers().keySet()) {
                if (!allowed.contains(key)) {
                    problems.add(prefix + key + ": unknown key (allowed: " + join(allowed) + ")");
                }
            }
        }

        private String code(StructuredValue object, String key, String where, boolean required) {
            String value = text(object, key, where);
            if (value == null) {
                if (required && !object.get(key).isPresent()) {
                    problems.add(where + ": missing");
                }
                return null;
            }
            if (!CODE.matcher(value).matches()) {
                problems.add(where + ": must match " + CODE.pattern() + " but was '" + value + "'");
                return null;
            }
            return value;
        }

        private int version(StructuredValue object) {
            StructuredValue v = object.get("version").orElse(null);
            if (v == null) {
                problems.add("version: missing");
                return 0;
            }
            OptionalLong n = v.asLong();
            if (!n.isPresent() || n.getAsLong() < 1 || n.getAsLong() > Integer.MAX_VALUE) {
                problems.add("version: must be a positive integer but was " + v);
                return 0;
            }
            return (int) n.getAsLong();
        }

        private String text(StructuredValue object, String key, String where) {
            StructuredValue v = object.get(key).orElse(null);
            if (v == null) {
                return null;
            }
            if (!v.isString()) {
                problems.add(where + ": must be a string");
                return null;
            }
            String s = v.asText().get().trim();
            if (s.isEmpty()) {
                problems.add(where + ": must not be blank");
                return null;
            }
            return s;
        }

        private boolean bool(StructuredValue object, String key, String where, boolean defaultValue) {
            StructuredValue v = object.get(key).orElse(null);
            if (v == null) {
                return defaultValue;
            }
            if (!v.isBoolean()) {
                problems.add(where + ": must be true or false");
                return defaultValue;
            }
            return v.asBoolean().get();
        }

        private TreePath path(StructuredValue object, String key, String where, boolean required) {
            String raw = text(object, key, where);
            if (raw == null) {
                if (required && !object.get(key).isPresent()) {
                    problems.add(where + ": missing");
                }
                return null;
            }
            return parsePath(raw, where);
        }

        private TreePath parsePath(String raw, String where) {
            try {
                return TreePath.parse(raw);
            } catch (IllegalArgumentException e) {
                problems.add(where + ": " + e.getMessage());
                return null;
            }
        }

        private List<String> stringList(StructuredValue v, String where) {
            if (v.isString()) {
                return Collections.singletonList(v.asText().get());
            }
            if (!v.isArray()) {
                problems.add(where + ": must be a string or a list of strings");
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<String>();
            for (StructuredValue e : v.getElements()) {
                if (!e.isString()) {
                    problems.add(where + ": must contain only strings");
                    continue;
                }
                out.add(e.asText().get());
            }
            if (out.isEmpty()) {
                problems.add(where + ": must not be empty");
            }
            return out;
        }

        private static String join(Iterable<String> names) {
            StringBuilder out = new StringBuilder();
            for (String n : names) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(n);
            }
            return out.toString();
        }
    }

    private enum FieldScope { METADATA, ELEMENT }
}
