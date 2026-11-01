package com.aresstack.serp4j.mapping.internal;

import com.aresstack.serp4j.core.model.feature.SerpFeatureType;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The symbolic target names of the mapping definitions and their value kinds (ADR-019 §2: "Ziele sind symbolische
 * Namen, die die Engine auf Builder-Methoden abbildet"). Single source of truth for validation and assignment.
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class Targets {

    /** Value kind of a target; decides the default converter and the coercion of converter results. */
    public enum Kind {
        TEXT("string"), INT("int"), LONG("long"), DECIMAL("decimal"), BOOLEAN("boolean"), DATE("string"),
        AREA("string"), TEXT_LIST("string"), EXTENSION(null);

        private final String defaultConverter;

        Kind(String defaultConverter) {
            this.defaultConverter = defaultConverter;
        }

        /** Converter applied when the field names none; {@code null} = keep the value (extensions). */
        public String getDefaultConverter() {
            return defaultConverter;
        }

        public boolean isList() {
            return this == TEXT_LIST;
        }
    }

    public static final Pattern EXTENSION_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static final Map<String, Kind> POSITION = ordered(
            "rankInGroup", Kind.INT, "rankAbsolute", Kind.INT, "page", Kind.INT, "area", Kind.AREA);

    private static final Map<String, Kind> ITEM = ordered(
            "url", Kind.TEXT, "title", Kind.TEXT, "snippet", Kind.TEXT, "additionalSnippets", Kind.TEXT_LIST,
            "displayUrl", Kind.TEXT, "domain", Kind.TEXT, "siteName", Kind.TEXT, "language", Kind.TEXT,
            "publishedDate", Kind.DATE, "thumbnail", Kind.TEXT);

    private static final Map<String, Kind> METADATA = ordered(
            "originalQuery", Kind.TEXT, "effectiveQuery", Kind.TEXT, "totalResultsEstimate", Kind.LONG,
            "hasMore", Kind.BOOLEAN, "pageNumber", Kind.INT, "providerRequestId", Kind.TEXT,
            "durationMillis", Kind.LONG, "cost", Kind.DECIMAL, "currency", Kind.TEXT, "credits", Kind.DECIMAL);

    private static final Map<String, Kind> GENERIC_FEATURE = ordered("title", Kind.TEXT);

    private static final Map<String, Map<String, Kind>> TYPED_FEATURES = new HashMap<String, Map<String, Kind>>();

    static {
        TYPED_FEATURES.put(SerpFeatureType.KNOWLEDGE_PANEL.getCode(), ordered(
                "title", Kind.TEXT, "subtitle", Kind.TEXT, "description", Kind.TEXT, "websiteUrl", Kind.TEXT,
                "images", Kind.TEXT_LIST));
        TYPED_FEATURES.put(SerpFeatureType.FEATURED_SNIPPET.getCode(), ordered(
                "text", Kind.TEXT, "sourceUrl", Kind.TEXT, "sourceTitle", Kind.TEXT, "sourceDomain", Kind.TEXT));
        Map<String, Kind> answerBox = ordered("text", Kind.TEXT, "sourceUrl", Kind.TEXT, "sourceTitle", Kind.TEXT);
        TYPED_FEATURES.put(SerpFeatureType.ANSWER_BOX.getCode(), answerBox);
        TYPED_FEATURES.put(SerpFeatureType.CALCULATOR.getCode(), answerBox);
        TYPED_FEATURES.put(SerpFeatureType.WEATHER.getCode(), answerBox);
        TYPED_FEATURES.put(SerpFeatureType.RELATED_SEARCHES.getCode(), ordered("queries", Kind.TEXT_LIST));
    }

    private Targets() {
    }

    private static Map<String, Kind> ordered(Object... pairs) {
        Map<String, Kind> map = new LinkedHashMap<String, Kind>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Kind) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /** {@code true} if {@code target} has the form {@code namespace:name}. */
    public static boolean isExtension(String target) {
        return target.indexOf(':') > 0;
    }

    public static boolean isPosition(String target) {
        return POSITION.containsKey(target);
    }

    /** Kind of an item target, or {@code null} if the target is not valid for items. */
    public static Kind forItem(String target) {
        if (isExtension(target)) {
            return Kind.EXTENSION;
        }
        Kind kind = ITEM.get(target);
        return kind != null ? kind : POSITION.get(target);
    }

    /** Kind of a feature target for the given feature type code, or {@code null} if not valid there. */
    public static Kind forFeature(String featureType, String target) {
        if (isExtension(target)) {
            return Kind.EXTENSION;
        }
        Kind position = POSITION.get(target);
        if (position != null) {
            return position;
        }
        Map<String, Kind> typed = TYPED_FEATURES.get(featureType);
        return (typed != null ? typed : GENERIC_FEATURE).get(target);
    }

    /** Kind of a metadata target, or {@code null} if not valid. */
    public static Kind forMetadata(String target) {
        return isExtension(target) ? Kind.EXTENSION : METADATA.get(target);
    }

    /** {@code true} if the engine builds a typed feature declaratively for this type. */
    public static boolean hasDeclarativeFeature(String featureType) {
        return TYPED_FEATURES.containsKey(featureType);
    }

    public static Iterable<String> itemTargets() {
        return ITEM.keySet();
    }

    public static Iterable<String> positionTargets() {
        return POSITION.keySet();
    }

    public static Iterable<String> metadataTargets() {
        return METADATA.keySet();
    }

    /** Declarative targets of a feature type (without position and extension targets). */
    public static Iterable<String> featureTargets(String featureType) {
        Map<String, Kind> typed = TYPED_FEATURES.get(featureType);
        return (typed != null ? typed : GENERIC_FEATURE).keySet();
    }
}
