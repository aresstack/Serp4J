package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.MediaInfo;
import com.aresstack.serp4j.core.model.data.PublishedDate;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.mapping.convert.ValueConverters;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;
import com.aresstack.serp4j.mapping.custom.FeatureMapper;
import com.aresstack.serp4j.mapping.custom.FeatureSeed;
import com.aresstack.serp4j.mapping.custom.ItemMapper;
import com.aresstack.serp4j.mapping.custom.MetadataMapper;
import com.aresstack.serp4j.mapping.internal.Coercion;
import com.aresstack.serp4j.mapping.internal.FeatureCopies;
import com.aresstack.serp4j.mapping.internal.Remainder;
import com.aresstack.serp4j.mapping.internal.Targets;
import com.aresstack.serp4j.mapping.path.PathLocation;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies one {@link MappingDefinition} – together with the {@link CustomMapperRegistry} that belongs to it and
 * a set of {@link ValueConverters} – to provider responses (ADR-019). An engine is bound to exactly one definition;
 * it refuses a registry created for another definition, so custom mappers can never leak between providers.
 *
 * <pre>{@code
 * MappingEngine engine = MappingEngine.builder(definition)
 *         .customMappers(CustomMapperRegistry.builder(definition)
 *                 .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, new KnowledgeGraphMapper())
 *                 .build())
 *         .provider(ProviderId.of("dataforseo"))
 *         .build();
 * MappingResult result = engine.map(JsonTrees.read(responseBody));
 * }</pre>
 *
 * <p>Order per response: declarative metadata, metadata mappers, then every section in definition order; within a
 * section every element in source order (declarative fields, then the item or feature mapper). Mapping never
 * throws because of the response: unknown element types become {@link GenericSerpFeature}s, elements without a
 * required field are skipped, values that cannot be converted are left out – each with a {@link SearchWarning}
 * whose details contain the element, so nothing is lost silently. Everything not consumed ends up in
 * {@code Extensions.unmapped(<namespace>)} of its element, the root rest in the metadata's extensions.</p>
 *
 * <p>Instances are immutable and thread-safe if the registered custom mappers and converters are.</p>
 */
public final class MappingEngine {

    private final MappingDefinition definition;
    private final ValueConverters converters;
    private final CustomMapperRegistry mappers;
    private final ProviderId provider;

    private MappingEngine(Builder builder) {
        this.definition = builder.definition;
        this.converters = builder.converters;
        this.mappers = builder.mappers != null ? builder.mappers : CustomMapperRegistry.empty(builder.definition);
        this.provider = builder.provider;
    }

    /** An engine with the standard converters and no custom mappers. */
    public static MappingEngine of(MappingDefinition definition) {
        return builder(definition).build();
    }

    public static Builder builder(MappingDefinition definition) {
        return new Builder(Objects.requireNonNull(definition, "definition"));
    }

    public MappingDefinition getDefinition() {
        return definition;
    }

    public ValueConverters getConverters() {
        return converters;
    }

    public CustomMapperRegistry getCustomMappers() {
        return mappers;
    }

    public Optional<ProviderId> getProvider() {
        return Optional.ofNullable(provider);
    }

    /** Maps one provider response. */
    public MappingResult map(StructuredValue response) {
        Objects.requireNonNull(response, "response");
        return new Run(response).execute();
    }

    @Override
    public String toString() {
        return "MappingEngine{" + definition + ", " + mappers + '}';
    }

    /** Builder; {@link #build()} validates converters and registry against the definition. */
    public static final class Builder {
        private final MappingDefinition definition;
        private ValueConverters converters = ValueConverters.standard();
        private CustomMapperRegistry mappers;
        private ProviderId provider;

        private Builder(MappingDefinition definition) {
            this.definition = definition;
        }

        /** Converters available to the definition; default {@link ValueConverters#standard()}. */
        public Builder converters(ValueConverters converters) {
            this.converters = Objects.requireNonNull(converters, "converters");
            return this;
        }

        /** The custom mappers; the registry must have been created for this engine's definition. */
        public Builder customMappers(CustomMapperRegistry mappers) {
            this.mappers = Objects.requireNonNull(mappers, "mappers");
            return this;
        }

        /** Provider the warnings are attributed to. */
        public Builder provider(ProviderId provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
            return this;
        }

        /**
         * @throws IllegalArgumentException          if the registry belongs to another definition
         * @throws InvalidMappingDefinitionException if the definition references converters that are not registered
         */
        public MappingEngine build() {
            if (mappers != null && !mappers.belongsTo(definition)) {
                throw new IllegalArgumentException("custom mapper registry belongs to " + mappers.getDefinition()
                        + ", not to " + definition + "; every definition needs its own registry (ADR-019)");
            }
            List<String> problems = new ArrayList<String>();
            checkConverters(definition.getMetadata(), "metadata", problems);
            for (SectionDefinition s : definition.getSections()) {
                checkConverters(s.getFields(), "section '" + s.getId() + "'.fields", problems);
                for (Map.Entry<String, List<FieldMapping>> e : s.getFieldsByType().entrySet()) {
                    checkConverters(e.getValue(), "section '" + s.getId() + "'.fieldsByType." + e.getKey(), problems);
                }
            }
            if (!problems.isEmpty()) {
                throw new InvalidMappingDefinitionException(definition.getId(), problems);
            }
            return new MappingEngine(this);
        }

        private void checkConverters(List<FieldMapping> fields, String where, List<String> problems) {
            for (FieldMapping f : fields) {
                if (f.getConverter().isPresent() && !converters.contains(f.getConverter().get())) {
                    problems.add(where + "." + f.getTarget() + ": unknown converter '" + f.getConverter().get()
                            + "' (registered: " + converters.getNames() + ")");
                }
            }
        }
    }

    // ================================================================== one mapping run

    private final class Run {
        private final StructuredValue root;
        private final MappingContext context;
        private final Set<PathLocation> rootConsumed = new HashSet<PathLocation>();
        private final SearchData.Builder data = SearchData.builder();

        Run(StructuredValue root) {
            this.root = root;
            this.context = new MappingContext(definition, converters, provider);
        }

        MappingResult execute() {
            MappedMetadata.Builder metadata = MappedMetadata.builder();
            SourceElement rootElement = SourceElement.root(root, rootConsumed);
            for (FieldMapping f : definition.getMetadata()) {
                Targets.Kind kind = Targets.forMetadata(f.getTarget());
                Object value = resolve(rootElement, f, kind, "metadata");
                if (value != null) {
                    assignMetadata(metadata, f.getTarget(), value);
                }
            }
            for (MetadataMapper mapper : mappers.getMetadataMappers()) {
                Set<PathLocation> snapshot = new HashSet<PathLocation>(rootConsumed);
                MappedMetadata before = metadata.build();
                try {
                    mapper.map(rootElement, metadata, context);
                } catch (RuntimeException e) {
                    rootConsumed.retainAll(snapshot);
                    metadata = before.toBuilder();
                    warn(SearchWarning.MAPPING_INCOMPLETE, "metadata mapper failed: " + e, details(null, null).build());
                }
            }
            for (SectionDefinition section : definition.getSections()) {
                mapSection(section);
            }
            if (definition.isKeepUnmappedRoot()) {
                StructuredValue rest = Remainder.of(root, rootConsumed);
                if (rest != null) {
                    metadata.unmapped(definition.getNamespace(), rest);
                }
            }
            return new MappingResult(definition, data.build(), metadata.build(), context.getWarnings());
        }

        // ---------------------------------------------------------- sections

        private void mapSection(SectionDefinition section) {
            List<TreePath.Match> matches = section.getPath().locate(root);
            Map<ElementType, Integer> counters = new HashMap<ElementType, Integer>();
            for (int i = 0; i < matches.size(); i++) {
                TreePath.Match match = matches.get(i);
                StructuredValue value = match.getValue();
                if (value.isNull()) {
                    rootConsumed.add(match.getLocation());
                    continue;
                }
                Set<PathLocation> consumed = new LinkedHashSet<PathLocation>();
                SourceElement element = SourceElement.at(value, match.getLocation(), consumed);

                ElementType type = section.getDefaultType().orElse(null);
                String discriminatorValue = null;
                if (section.getDiscriminator().isPresent()) {
                    discriminatorValue = firstText(element, section.getDiscriminator().get());
                    if (discriminatorValue != null) {
                        if (section.getSkipWhen().contains(discriminatorValue)) {
                            continue; // deliberately not mapped: stays in the root's unmapped rest
                        }
                        type = section.getDiscriminatorMap().get(discriminatorValue);
                    }
                }
                rootConsumed.add(match.getLocation());
                ElementId id = ElementId.of(section.getId() + "#" + (i + 1));
                if (type == null) {
                    unknownElement(section, id, element, discriminatorValue);
                    continue;
                }
                Integer previous = counters.get(type);
                int rank = previous == null ? 1 : previous + 1;
                counters.put(type, rank);
                mapElement(section, type, id, rank, element, consumed);
            }
        }

        private void unknownElement(SectionDefinition section, ElementId id, SourceElement element, String typeValue) {
            SerpFeatureType type = SerpFeatureType.of(sanitize(typeValue));
            data.feature(GenericSerpFeature.builder(id, type, element.getValue()).build());
            StructuredValue.ObjectBuilder details = details(section, element.getLocation());
            if (typeValue != null) {
                details.put("discriminator", typeValue);
            }
            warn(SearchWarning.UNKNOWN_ELEMENT, "section '" + section.getId() + "': unknown element type '"
                    + (typeValue != null ? typeValue : "?") + "' at " + element.getLocation()
                    + ", kept as generic feature " + id.getValue(), details.build());
        }

        private void mapElement(SectionDefinition section, ElementType type, ElementId id, int rank,
                                SourceElement element, Set<PathLocation> consumed) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            Extensions.Builder extensions = Extensions.builder();
            List<String> missing = new ArrayList<String>();
            // With a feature mapper, the declarative values of ordinary feature fields are discarded (only position
            // and extensions reach the seed). Their sources must therefore not count as consumed unless the
            // declarative path is taken, or they would vanish from both the feature and its unmapped rest.
            boolean defer = !type.isItem()
                    && mappers.getFeatureMapper(section.getId(), type.getFeatureType().get()).isPresent();
            Set<PathLocation> deferred = new HashSet<PathLocation>();
            for (FieldMapping f : fieldOrder(section.fieldsFor(type), defer)) {
                Targets.Kind kind = type.isItem() ? Targets.forItem(f.getTarget())
                        : Targets.forFeature(type.getCode(), f.getTarget());
                if (kind == null) {
                    continue; // common field that this type does not support; the source stays unmapped
                }
                boolean ordinary = defer && isOrdinary(f, kind);
                Set<PathLocation> before = ordinary ? new HashSet<PathLocation>(consumed) : null;
                Object value = resolve(element, f, kind, "section '" + section.getId() + "'");
                if (ordinary) {
                    for (PathLocation l : consumed) {
                        if (!before.contains(l)) {
                            deferred.add(l);
                        }
                    }
                }
                if (value == null) {
                    if (f.isRequired()) {
                        missing.add(f.getTarget());
                    }
                } else if (kind == Targets.Kind.EXTENSION) {
                    putExtension(extensions, f.getTarget(), value, section, element);
                } else {
                    values.put(f.getTarget(), value);
                }
            }
            if (type.isItem() && !values.containsKey("url") && !missing.contains("url")) {
                missing.add("url");
            }
            if (!missing.isEmpty()) {
                StructuredValue.ObjectBuilder details = details(section, element.getLocation());
                details.put("type", type.toString());
                details.put("element", element.getValue());
                warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': element at "
                        + element.getLocation() + " skipped, required field(s) missing: " + missing, details.build());
                return;
            }
            SerpPosition position = position(section, rank, values, element);
            if (type.isItem()) {
                mapItem(section, type.getResultType().get(), id, values, position, extensions.build(), element, consumed);
            } else {
                mapFeature(section, type.getFeatureType().get(), id, values, position, extensions.build(), element,
                        consumed, deferred);
            }
        }

        /** Seed-relevant fields (position, extensions) first, so a location shared with them is never deferred. */
        private List<FieldMapping> fieldOrder(List<FieldMapping> fields, boolean defer) {
            if (!defer) {
                return fields;
            }
            List<FieldMapping> seed = new ArrayList<FieldMapping>();
            List<FieldMapping> ordinary = new ArrayList<FieldMapping>();
            for (FieldMapping f : fields) {
                if (Targets.isPosition(f.getTarget()) || Targets.isExtension(f.getTarget())) {
                    seed.add(f);
                } else {
                    ordinary.add(f);
                }
            }
            seed.addAll(ordinary);
            return seed;
        }

        private boolean isOrdinary(FieldMapping f, Targets.Kind kind) {
            return kind != Targets.Kind.EXTENSION && !Targets.isPosition(f.getTarget());
        }

        // ---------------------------------------------------------- items

        private void mapItem(SectionDefinition section, ResultType type, ElementId id, Map<String, Object> v,
                             SerpPosition position, Extensions extensions, SourceElement element,
                             Set<PathLocation> consumed) {
            SearchResultItem.Builder b;
            try {
                b = SearchResultItem.builder(id, type, (String) v.get("url"));
            } catch (IllegalArgumentException e) {
                skipped(section, element, "invalid url: " + e.getMessage());
                return;
            }
            for (Map.Entry<String, Object> e : v.entrySet()) {
                try {
                    assignItem(b, e.getKey(), e.getValue());
                } catch (IllegalArgumentException ex) {
                    fieldFailed(section, element, e.getKey(), ex.getMessage());
                }
            }
            if (position != null) {
                b.position(position);
            }
            SearchResultItem item = b.extensions(extensions).build();

            Optional<ItemMapper> mapper = mappers.getItemMapper(section.getId());
            if (mapper.isPresent()) {
                Set<PathLocation> snapshot = new HashSet<PathLocation>(consumed);
                try {
                    SearchResultItem mapped = mapper.get().map(element, item, context);
                    if (mapped == null) {
                        throw new IllegalStateException("item mapper returned null");
                    }
                    if (!mapped.getId().equals(id)) {
                        throw new IllegalStateException("item mapper changed the element id to " + mapped.getId());
                    }
                    item = mapped;
                } catch (RuntimeException e) {
                    consumed.retainAll(snapshot);
                    customFailed(section, element, "item mapper", e);
                }
            }
            if (section.isKeepUnmapped()) {
                StructuredValue rest = Remainder.of(element.getValue(), consumed);
                if (rest != null) {
                    item = item.toBuilder()
                            .extensions(item.getExtensions().withUnmapped(definition.getNamespace(), rest)).build();
                }
            }
            data.result(item);
        }

        private void assignItem(SearchResultItem.Builder b, String target, Object value) {
            if ("url".equals(target) || Targets.isPosition(target)) {
                return;
            }
            if ("title".equals(target)) {
                b.title((String) value);
            } else if ("snippet".equals(target)) {
                b.snippet((String) value);
            } else if ("additionalSnippets".equals(target)) {
                for (Object s : (List<?>) value) {
                    b.additionalSnippet((String) s);
                }
            } else if ("displayUrl".equals(target)) {
                b.displayUrl((String) value);
            } else if ("domain".equals(target)) {
                b.domain((String) value);
            } else if ("siteName".equals(target)) {
                b.siteName((String) value);
            } else if ("language".equals(target)) {
                b.language((String) value);
            } else if ("publishedDate".equals(target)) {
                b.publishedDate(value instanceof Instant ? PublishedDate.of((Instant) value)
                        : PublishedDate.ofText((String) value));
            } else if ("thumbnail".equals(target)) {
                b.thumbnail(MediaInfo.of((String) value));
            }
        }

        // ---------------------------------------------------------- features

        private void mapFeature(SectionDefinition section, SerpFeatureType type, ElementId id, Map<String, Object> v,
                                SerpPosition position, Extensions declared, SourceElement element,
                                Set<PathLocation> consumed, Set<PathLocation> deferred) {
            SerpFeature feature = null;
            Optional<FeatureMapper> mapper = mappers.getFeatureMapper(section.getId(), type);
            if (mapper.isPresent()) {
                consumed.removeAll(deferred);
                Set<PathLocation> snapshot = new HashSet<PathLocation>(consumed);
                try {
                    feature = mapper.get().map(element, new FeatureSeed(id, type, section.getId(), position, declared),
                            context);
                    if (feature != null && !feature.getId().equals(id)) {
                        throw new IllegalStateException("feature mapper used id " + feature.getId() + " instead of " + id);
                    }
                } catch (RuntimeException e) {
                    feature = null;
                    customFailed(section, element, "feature mapper", e);
                }
                if (feature == null) {
                    consumed.retainAll(snapshot);
                    consumed.addAll(deferred); // declarative path: its fields are consumed after all
                }
            }
            if (feature != null) {
                data.feature(finish(section, feature, position, declared, element, consumed));
                return;
            }
            if (Targets.hasDeclarativeFeature(type.getCode())) {
                SerpFeature typed = null;
                String problem;
                try {
                    typed = assembleFeature(type, id, v);
                    problem = typed == null ? "required values for a " + type.getCode() + " feature missing" : null;
                } catch (IllegalArgumentException e) {
                    problem = e.getMessage();
                }
                if (typed != null) {
                    data.feature(finish(section, typed, position, declared, element, consumed));
                    return;
                }
                StructuredValue.ObjectBuilder details = details(section, element.getLocation());
                details.put("type", type.getCode());
                warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': element at "
                        + element.getLocation() + " kept as generic " + type.getCode() + " feature: " + problem,
                        details.build());
            }
            GenericSerpFeature.Builder generic = GenericSerpFeature.builder(id, type, element.getValue())
                    .title((String) v.get("title"))
                    .extensions(declared);
            if (position != null) {
                generic.position(position);
            }
            data.feature(generic.build());
        }

        private SerpFeature finish(SectionDefinition section, SerpFeature feature, SerpPosition position,
                                   Extensions declared, SourceElement element, Set<PathLocation> consumed) {
            Extensions extensions = feature.getExtensions().merge(declared);
            StructuredValue rest = section.isKeepUnmapped() ? Remainder.of(element.getValue(), consumed) : null;
            if (rest != null) {
                extensions = extensions.withUnmapped(definition.getNamespace(), rest);
            }
            SerpPosition finalPosition = feature.getPosition().isPresent() ? feature.getPosition().get() : position;
            SerpFeature copy = FeatureCopies.with(feature, finalPosition, extensions);
            if (copy != null) {
                return copy;
            }
            if (rest != null) {
                StructuredValue.ObjectBuilder details = details(section, element.getLocation());
                details.put("unmapped", rest);
                warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': feature class "
                        + feature.getClass().getName() + " cannot be copied, unmapped rest kept in this warning",
                        details.build());
            }
            return feature;
        }

        private SerpFeature assembleFeature(SerpFeatureType type, ElementId id, Map<String, Object> v) {
            String code = type.getCode();
            if (code.equals(SerpFeatureType.KNOWLEDGE_PANEL.getCode())) {
                String title = (String) v.get("title");
                if (title == null) {
                    return null;
                }
                KnowledgePanelFeature.Builder b = KnowledgePanelFeature.builder(id, title)
                        .subtitle((String) v.get("subtitle"))
                        .description((String) v.get("description"))
                        .websiteUrl((String) v.get("websiteUrl"));
                Object images = v.get("images");
                if (images != null) {
                    for (Object url : (List<?>) images) {
                        b.image(MediaInfo.of((String) url));
                    }
                }
                return b.build();
            }
            if (code.equals(SerpFeatureType.FEATURED_SNIPPET.getCode())) {
                String text = (String) v.get("text");
                String url = (String) v.get("sourceUrl");
                if (text == null || url == null) {
                    return null;
                }
                SourceReference source = SourceReference.builder(url)
                        .title((String) v.get("sourceTitle"))
                        .domain((String) v.get("sourceDomain"))
                        .build();
                return FeaturedSnippetFeature.builder(id, text, source).build();
            }
            if (code.equals(SerpFeatureType.RELATED_SEARCHES.getCode())) {
                Object queries = v.get("queries");
                if (queries == null || ((List<?>) queries).isEmpty()) {
                    return null;
                }
                RelatedQueriesFeature.Builder b = RelatedQueriesFeature.builder(id);
                for (Object q : (List<?>) queries) {
                    b.query((String) q);
                }
                return b.build();
            }
            // answer_box, calculator, weather
            String text = (String) v.get("text");
            if (text == null) {
                return null;
            }
            AnswerBoxFeature.Builder b = AnswerBoxFeature.builder(id, type, text);
            String url = (String) v.get("sourceUrl");
            if (url != null) {
                b.source(SourceReference.of(url, (String) v.get("sourceTitle")));
            }
            return b.build();
        }

        // ---------------------------------------------------------- values

        /** Value of the first source with a value (a {@code List} for list targets); {@code null} if none. */
        private Object resolve(SourceElement element, FieldMapping f, Targets.Kind kind, String where) {
            List<StructuredValue> raw = new ArrayList<StructuredValue>();
            for (TreePath source : f.getSources()) {
                List<TreePath.Match> hits = new ArrayList<TreePath.Match>();
                for (TreePath.Match m : source.locate(element.getValue())) {
                    if (!m.getValue().isNull()) {
                        hits.add(m);
                    }
                }
                if (hits.isEmpty()) {
                    continue;
                }
                if (kind.isList()) {
                    for (TreePath.Match m : hits) {
                        element.mark(m.getLocation());
                        addFlattened(raw, m.getValue());
                    }
                } else {
                    element.mark(hits.get(0).getLocation());
                    raw.add(hits.get(0).getValue());
                }
                break;
            }
            if (raw.isEmpty() && f.getDefault().isPresent()) {
                if (kind.isList()) {
                    addFlattened(raw, f.getDefault().get());
                } else {
                    raw.add(f.getDefault().get());
                }
            }
            if (raw.isEmpty()) {
                return null;
            }
            String converter = f.getConverter().orElse(kind.getDefaultConverter());
            List<Object> converted = new ArrayList<Object>();
            for (StructuredValue r : raw) {
                try {
                    Object c = converter == null ? r : context.convert(converter, r);
                    Object value = Coercion.coerce(c, kind);
                    if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
                        converted.add(value);
                    }
                } catch (RuntimeException e) {
                    StructuredValue.ObjectBuilder details = details(null, element.getLocation());
                    details.put("field", f.getTarget());
                    details.put("value", r);
                    warn(SearchWarning.MAPPING_INCOMPLETE, where + ": field '" + f.getTarget() + "' at "
                            + element.getLocation() + ": cannot convert " + r + ": " + e.getMessage(), details.build());
                    if (!kind.isList()) {
                        return null;
                    }
                }
            }
            if (converted.isEmpty()) {
                return null;
            }
            return kind.isList() ? Collections.unmodifiableList(converted) : converted.get(0);
        }

        private void addFlattened(List<StructuredValue> out, StructuredValue value) {
            if (value.isArray()) {
                for (StructuredValue e : value.getElements()) {
                    if (!e.isNull()) {
                        out.add(e);
                    }
                }
            } else {
                out.add(value);
            }
        }

        private String firstText(SourceElement element, TreePath path) {
            for (TreePath.Match m : path.locate(element.getValue())) {
                StructuredValue v = m.getValue();
                if (v.isNull()) {
                    continue;
                }
                element.mark(m.getLocation());
                if (v.isString()) {
                    return v.asText().get();
                }
                if (v.isNumber()) {
                    return v.asNumber().get().toPlainString();
                }
                if (v.isBoolean()) {
                    return v.asBoolean().get().toString();
                }
                return null;
            }
            return null;
        }

        private SerpPosition position(SectionDefinition section, int rank, Map<String, Object> v,
                                      SourceElement element) {
            if (section.getPosition() == PositionMode.NONE) {
                return null;
            }
            Integer rankInGroup = section.getPosition() == PositionMode.INDEX ? Integer.valueOf(rank) : null;
            if (v.get("rankInGroup") != null) {
                rankInGroup = (Integer) v.get("rankInGroup");
            }
            Integer rankAbsolute = (Integer) v.get("rankAbsolute");
            Integer page = (Integer) v.get("page");
            SerpArea area = (SerpArea) v.get("area");
            if (rankInGroup == null && rankAbsolute == null && page == null && area == null) {
                return null;
            }
            SerpPosition.Builder b = SerpPosition.builder();
            try {
                if (rankInGroup != null) {
                    b.rankInGroup(rankInGroup);
                }
                if (rankAbsolute != null) {
                    b.rankAbsolute(rankAbsolute);
                }
                if (page != null) {
                    b.page(page);
                }
                if (area != null) {
                    b.area(area);
                }
                return b.build();
            } catch (IllegalArgumentException e) {
                fieldFailed(section, element, "position", e.getMessage());
                return null;
            }
        }

        private void putExtension(Extensions.Builder extensions, String target, Object value,
                                  SectionDefinition section, SourceElement element) {
            String name = target.substring(target.indexOf(':') + 1);
            try {
                putTyped(extensions, name, value);
            } catch (IllegalArgumentException e) {
                fieldFailed(section, element, target, e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        private <T> void putTyped(Extensions.Builder extensions, String name, Object value) {
            Class<T> type = (Class<T>) Coercion.extensionType(value);
            extensions.put(ExtensionKey.of(definition.getNamespace(), name, type), type.cast(value));
        }

        @SuppressWarnings("unchecked")
        private <T> void putTyped(MappedMetadata.Builder metadata, String name, Object value) {
            Class<T> type = (Class<T>) Coercion.extensionType(value);
            metadata.extension(ExtensionKey.of(definition.getNamespace(), name, type), type.cast(value));
        }

        private void assignMetadata(MappedMetadata.Builder m, String target, Object value) {
            try {
                if (Targets.isExtension(target)) {
                    putTyped(m, target.substring(target.indexOf(':') + 1), value);
                } else if ("originalQuery".equals(target)) {
                    m.originalQuery((String) value);
                } else if ("effectiveQuery".equals(target)) {
                    m.effectiveQuery((String) value);
                } else if ("totalResultsEstimate".equals(target)) {
                    m.totalResultsEstimate((Long) value);
                } else if ("hasMore".equals(target)) {
                    m.hasMore((Boolean) value);
                } else if ("pageNumber".equals(target)) {
                    m.pageNumber((Integer) value);
                } else if ("providerRequestId".equals(target)) {
                    m.providerRequestId((String) value);
                } else if ("durationMillis".equals(target)) {
                    m.duration(Duration.ofMillis((Long) value));
                } else if ("cost".equals(target)) {
                    m.cost((BigDecimal) value);
                } else if ("currency".equals(target)) {
                    m.currency((String) value);
                } else if ("creditsUsed".equals(target)) {
                    m.creditsUsed((Long) value);
                }
            } catch (IllegalArgumentException e) {
                StructuredValue.ObjectBuilder details = details(null, null);
                details.put("field", target);
                warn(SearchWarning.MAPPING_INCOMPLETE, "metadata: field '" + target + "': " + e.getMessage(),
                        details.build());
            }
        }

        // ---------------------------------------------------------- warnings

        private void skipped(SectionDefinition section, SourceElement element, String reason) {
            StructuredValue.ObjectBuilder details = details(section, element.getLocation());
            details.put("element", element.getValue());
            warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': element at "
                    + element.getLocation() + " skipped: " + reason, details.build());
        }

        private void fieldFailed(SectionDefinition section, SourceElement element, String field, String reason) {
            StructuredValue.ObjectBuilder details = details(section, element.getLocation());
            details.put("field", field);
            warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': field '" + field + "' at "
                    + element.getLocation() + " left out: " + reason, details.build());
        }

        private void customFailed(SectionDefinition section, SourceElement element, String what, RuntimeException e) {
            StructuredValue.ObjectBuilder details = details(section, element.getLocation());
            details.put("error", e.toString());
            warn(SearchWarning.MAPPING_INCOMPLETE, "section '" + section.getId() + "': " + what + " failed at "
                    + element.getLocation() + ", declarative result kept: " + e, details.build());
        }

        private StructuredValue.ObjectBuilder details(SectionDefinition section, PathLocation location) {
            StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder()
                    .put("definition", definition.getId())
                    .put("version", definition.getVersion());
            if (section != null) {
                b.put("section", section.getId());
            }
            if (location != null) {
                b.put("location", location.toString());
            }
            return b;
        }

        private void warn(String code, String message, StructuredValue details) {
            context.warn(code, message, details);
        }
    }

    /** Turns any discriminator value into a valid feature type code. */
    static String sanitize(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(ok ? c : '_');
        }
        while (out.length() > 0 && !Character.isLetterOrDigit(out.charAt(0))) {
            out.deleteCharAt(0);
        }
        return out.length() == 0 ? "unknown" : out.toString();
    }
}
