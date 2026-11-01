package com.aresstack.serp4j.mapping.verify;

import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.AnswerSegment;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GeneratedAnswerFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQuery;
import com.aresstack.serp4j.core.model.feature.ResultGroupFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureVisitor;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.mapping.MappedMetadata;
import com.aresstack.serp4j.mapping.MappingResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, human-readable rendering of a {@link MappingResult} for golden-file tests of mapping definitions
 * (ADR-019 §5: "Golden-Output je Fixture"). The snapshot contains every mapped value, the extensions (typed values
 * by key, unmapped rest by namespace) and the warnings (code and message), in a stable order.
 *
 * <p>This is a test format, not the JSON format of the domain model (that is part J2 of {@code serp4j-json}) and
 * not meant to be parsed back.</p>
 */
public final class MappingSnapshot {

    private MappingSnapshot() {
    }

    /** The snapshot as a tree. */
    public static StructuredValue of(MappingResult result) {
        Objects.requireNonNull(result, "result");
        StructuredValue.ObjectBuilder root = StructuredValue.objectBuilder()
                .put("definition", result.getDefinitionId())
                .put("version", result.getDefinitionVersion());
        StructuredValue.ArrayBuilder results = StructuredValue.arrayBuilder();
        for (SearchResultItem item : result.getData().getResults()) {
            results.add(item(item));
        }
        root.put("results", results.build());
        StructuredValue.ArrayBuilder features = StructuredValue.arrayBuilder();
        for (SerpFeature feature : result.getData().getFeatures()) {
            features.add(feature(feature));
        }
        root.put("features", features.build());
        root.put("metadata", metadata(result.getMetadata()));
        StructuredValue.ArrayBuilder warnings = StructuredValue.arrayBuilder();
        for (SearchWarning w : result.getWarnings()) {
            warnings.add(StructuredValue.objectBuilder().put("code", w.getCode()).put("message", w.getMessage()).build());
        }
        root.put("warnings", warnings.build());
        return root.build();
    }

    /** The snapshot as indented JSON text with {@code \n} line ends and a final newline. */
    public static String render(MappingResult result) {
        return pretty(of(result));
    }

    /** Indented JSON rendering of any tree (two spaces, {@code \n}, final newline). */
    public static String pretty(StructuredValue value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    // ------------------------------------------------------------------ elements

    private static StructuredValue item(SearchResultItem item) {
        StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder()
                .put("id", item.getId().getValue())
                .put("type", item.getType().getCode())
                .put("url", item.getUrl());
        opt(b, "title", item.getTitle());
        opt(b, "snippet", item.getSnippet());
        if (!item.getAdditionalSnippets().isEmpty()) {
            b.put("additionalSnippets", strings(item.getAdditionalSnippets()));
        }
        opt(b, "displayUrl", item.getDisplayUrl());
        opt(b, "domain", item.getDomain());
        opt(b, "siteName", item.getSiteName());
        opt(b, "language", item.getLanguage());
        if (item.getPublishedDate().isPresent()) {
            StructuredValue.ObjectBuilder d = StructuredValue.objectBuilder();
            if (item.getPublishedDate().get().getInstant().isPresent()) {
                d.put("instant", item.getPublishedDate().get().getInstant().get().toString());
            }
            opt(d, "text", item.getPublishedDate().get().getDisplayText());
            b.put("publishedDate", d.build());
        }
        if (item.getThumbnail().isPresent()) {
            b.put("thumbnail", item.getThumbnail().get().getUrl());
        }
        position(b, item.getPosition());
        extensions(b, item.getExtensions());
        return b.build();
    }

    private static StructuredValue feature(SerpFeature feature) {
        final StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder()
                .put("id", feature.getId().getValue())
                .put("type", feature.getType().getCode());
        feature.accept(new SerpFeatureVisitor<Void>() {
            @Override
            public Void visitKnowledgePanel(KnowledgePanelFeature f) {
                b.put("class", "KnowledgePanelFeature").put("title", f.getTitle());
                opt(b, "subtitle", f.getSubtitle());
                opt(b, "description", f.getDescription());
                if (f.getDescriptionSource().isPresent()) {
                    b.put("descriptionSource", source(f.getDescriptionSource().get()));
                }
                if (!f.getFacts().isEmpty()) {
                    StructuredValue.ArrayBuilder facts = StructuredValue.arrayBuilder();
                    for (Fact fact : f.getFacts()) {
                        StructuredValue.ArrayBuilder values = StructuredValue.arrayBuilder();
                        for (Fact.Value v : fact.getValues()) {
                            StructuredValue.ObjectBuilder vb = StructuredValue.objectBuilder().put("text", v.getText());
                            opt(vb, "url", v.getUrl());
                            values.add(vb.build());
                        }
                        facts.add(StructuredValue.objectBuilder().put("key", fact.getKey()).put("values", values.build()).build());
                    }
                    b.put("facts", facts.build());
                }
                if (!f.getImages().isEmpty()) {
                    StructuredValue.ArrayBuilder images = StructuredValue.arrayBuilder();
                    for (com.aresstack.serp4j.core.model.data.MediaInfo m : f.getImages()) {
                        images.add(m.getUrl());
                    }
                    b.put("images", images.build());
                }
                opt(b, "websiteUrl", f.getWebsiteUrl());
                return null;
            }

            @Override
            public Void visitGeneratedAnswer(GeneratedAnswerFeature f) {
                b.put("class", "GeneratedAnswerFeature").put("text", f.getText());
                opt(b, "markdown", f.getMarkdown());
                if (!f.getSegments().isEmpty()) {
                    StructuredValue.ArrayBuilder segments = StructuredValue.arrayBuilder();
                    for (AnswerSegment s : f.getSegments()) {
                        StructuredValue.ArrayBuilder refs = StructuredValue.arrayBuilder();
                        for (Integer i : s.getReferenceIndexes()) {
                            refs.add(i.longValue());
                        }
                        segments.add(StructuredValue.objectBuilder().put("text", s.getText())
                                .put("references", refs.build()).build());
                    }
                    b.put("segments", segments.build());
                }
                if (!f.getReferences().isEmpty()) {
                    StructuredValue.ArrayBuilder refs = StructuredValue.arrayBuilder();
                    for (SourceReference r : f.getReferences()) {
                        refs.add(source(r));
                    }
                    b.put("references", refs.build());
                }
                if (!f.getFollowUpSuggestions().isEmpty()) {
                    b.put("followUpSuggestions", strings(f.getFollowUpSuggestions()));
                }
                return null;
            }

            @Override
            public Void visitFeaturedSnippet(FeaturedSnippetFeature f) {
                b.put("class", "FeaturedSnippetFeature").put("text", f.getText()).put("source", source(f.getSource()));
                return null;
            }

            @Override
            public Void visitQuestionAnswer(QuestionAnswerFeature f) {
                b.put("class", "QuestionAnswerFeature");
                StructuredValue.ArrayBuilder questions = StructuredValue.arrayBuilder();
                for (QuestionAnswer q : f.getQuestions()) {
                    StructuredValue.ObjectBuilder qb = StructuredValue.objectBuilder().put("question", q.getQuestion());
                    opt(qb, "answer", q.getAnswer());
                    if (q.getSource().isPresent()) {
                        qb.put("source", source(q.getSource().get()));
                    }
                    questions.add(qb.build());
                }
                b.put("questions", questions.build());
                return null;
            }

            @Override
            public Void visitRelatedQueries(RelatedQueriesFeature f) {
                b.put("class", "RelatedQueriesFeature");
                StructuredValue.ArrayBuilder queries = StructuredValue.arrayBuilder();
                for (RelatedQuery q : f.getQueries()) {
                    StructuredValue.ObjectBuilder qb = StructuredValue.objectBuilder().put("text", q.getText());
                    opt(qb, "url", q.getUrl());
                    queries.add(qb.build());
                }
                b.put("queries", queries.build());
                return null;
            }

            @Override
            public Void visitResultGroup(ResultGroupFeature f) {
                b.put("class", "ResultGroupFeature");
                opt(b, "title", f.getTitle());
                StructuredValue.ArrayBuilder items = StructuredValue.arrayBuilder();
                for (ElementId id : f.getItems()) {
                    items.add(id.getValue());
                }
                b.put("items", items.build());
                return null;
            }

            @Override
            public Void visitAnswerBox(AnswerBoxFeature f) {
                b.put("class", "AnswerBoxFeature").put("text", f.getText());
                if (f.getSource().isPresent()) {
                    b.put("source", source(f.getSource().get()));
                }
                return null;
            }

            @Override
            public Void visitGeneric(GenericSerpFeature f) {
                b.put("class", "GenericSerpFeature");
                opt(b, "title", f.getTitle());
                b.put("content", f.getContent());
                return null;
            }

            @Override
            public Void visitOther(SerpFeature f) {
                b.put("class", f.getClass().getName());
                return null;
            }
        });
        position(b, feature.getPosition());
        extensions(b, feature.getExtensions());
        return b.build();
    }

    private static StructuredValue metadata(MappedMetadata m) {
        StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder();
        opt(b, "originalQuery", m.getOriginalQuery());
        opt(b, "effectiveQuery", m.getEffectiveQuery());
        if (m.getTotalResultsEstimate().isPresent()) {
            b.put("totalResultsEstimate", m.getTotalResultsEstimate().getAsLong());
        }
        if (m.getHasMore().isPresent()) {
            b.put("hasMore", m.getHasMore().get());
        }
        if (m.getPageNumber().isPresent()) {
            b.put("pageNumber", m.getPageNumber().getAsInt());
        }
        opt(b, "providerRequestId", m.getProviderRequestId());
        if (m.getDuration().isPresent()) {
            b.put("durationMillis", m.getDuration().get().toMillis());
        }
        if (m.getCost().isPresent()) {
            b.put("cost", StructuredValue.of(m.getCost().get()));
        }
        opt(b, "currency", m.getCurrency());
        if (m.getCreditsUsed().isPresent()) {
            b.put("creditsUsed", m.getCreditsUsed().getAsLong());
        }
        extensions(b, m.getExtensions());
        return b.build();
    }

    // ------------------------------------------------------------------ parts

    private static StructuredValue source(SourceReference r) {
        StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder().put("url", r.getUrl());
        opt(b, "title", r.getTitle());
        opt(b, "snippet", r.getSnippet());
        opt(b, "domain", r.getDomain());
        opt(b, "sourceName", r.getSourceName());
        if (r.getMatchingResult().isPresent()) {
            b.put("matchingResult", r.getMatchingResult().get().getValue());
        }
        return b.build();
    }

    private static void position(StructuredValue.ObjectBuilder b, Optional<SerpPosition> position) {
        if (!position.isPresent()) {
            return;
        }
        SerpPosition p = position.get();
        StructuredValue.ObjectBuilder pb = StructuredValue.objectBuilder();
        if (p.getRankInGroup().isPresent()) {
            pb.put("rankInGroup", p.getRankInGroup().getAsInt());
        }
        if (p.getRankAbsolute().isPresent()) {
            pb.put("rankAbsolute", p.getRankAbsolute().getAsInt());
        }
        if (p.getPage().isPresent()) {
            pb.put("page", p.getPage().getAsInt());
        }
        pb.put("area", p.getArea().name().toLowerCase(java.util.Locale.ROOT));
        b.put("position", pb.build());
    }

    private static void extensions(StructuredValue.ObjectBuilder b, Extensions extensions) {
        if (extensions.isEmpty()) {
            return;
        }
        StructuredValue.ObjectBuilder eb = StructuredValue.objectBuilder();
        if (!extensions.getKeys().isEmpty()) {
            List<ExtensionKey<?>> keys = new ArrayList<ExtensionKey<?>>(extensions.getKeys());
            Collections.sort(keys, new Comparator<ExtensionKey<?>>() {
                @Override
                public int compare(ExtensionKey<?> a, ExtensionKey<?> c) {
                    return a.toString().compareTo(c.toString());
                }
            });
            StructuredValue.ObjectBuilder values = StructuredValue.objectBuilder();
            for (ExtensionKey<?> key : keys) {
                values.put(key.toString(), extensionValue(extensions.get(key).get()));
            }
            eb.put("values", values.build());
        }
        if (!extensions.getUnmappedNamespaces().isEmpty()) {
            StructuredValue.ObjectBuilder unmapped = StructuredValue.objectBuilder();
            for (String ns : extensions.getUnmappedNamespaces()) {
                unmapped.put(ns, extensions.getUnmapped(ns).get());
            }
            eb.put("unmapped", unmapped.build());
        }
        b.put("extensions", eb.build());
    }

    private static StructuredValue extensionValue(Object value) {
        if (value instanceof StructuredValue) {
            return (StructuredValue) value;
        }
        if (value instanceof String) {
            return StructuredValue.of((String) value);
        }
        if (value instanceof Boolean) {
            return StructuredValue.of((Boolean) value);
        }
        if (value instanceof BigDecimal) {
            return StructuredValue.of((BigDecimal) value);
        }
        if (value instanceof Integer || value instanceof Long) {
            return StructuredValue.of(((Number) value).longValue());
        }
        return StructuredValue.of(value.getClass().getSimpleName() + ":" + value);
    }

    private static StructuredValue strings(List<String> values) {
        StructuredValue.ArrayBuilder a = StructuredValue.arrayBuilder();
        for (String v : values) {
            a.add(v);
        }
        return a.build();
    }

    private static void opt(StructuredValue.ObjectBuilder b, String name, Optional<String> value) {
        if (value.isPresent()) {
            b.put(name, value.get());
        }
    }

    // ------------------------------------------------------------------ pretty printer

    private static void write(StringBuilder out, StructuredValue v, int indent) {
        if (v.isObject()) {
            Map<String, StructuredValue> members = v.getMembers();
            if (members.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<String, StructuredValue> e : members.entrySet()) {
                indent(out, indent + 1);
                out.append(StructuredValue.of(e.getKey())).append(": ");
                write(out, e.getValue(), indent + 1);
                out.append(++i < members.size() ? ",\n" : "\n");
            }
            indent(out, indent);
            out.append('}');
        } else if (v.isArray()) {
            List<StructuredValue> elements = v.getElements();
            if (elements.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < elements.size(); i++) {
                indent(out, indent + 1);
                write(out, elements.get(i), indent + 1);
                out.append(i + 1 < elements.size() ? ",\n" : "\n");
            }
            indent(out, indent);
            out.append(']');
        } else {
            out.append(v);
        }
    }

    private static void indent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append("  ");
        }
    }
}
