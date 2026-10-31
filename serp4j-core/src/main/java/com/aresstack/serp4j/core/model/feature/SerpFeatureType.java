package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Codes;

/**
 * Type code of a {@link SerpFeature}. Open value type (ADR-004): unknown provider blocks keep their original type
 * code in a {@link GenericSerpFeature} (ADR-006). The constants cover the typed features of the core.
 */
public final class SerpFeatureType {

    public static final SerpFeatureType KNOWLEDGE_PANEL = of("knowledge_panel");
    /** AI answer shown on a normal result page (Google AI Overview); see architecture.md §5.5. */
    public static final SerpFeatureType AI_OVERVIEW = of("ai_overview");
    /** Answer of the conversational AI surface (Google AI Mode); see ADR-005 and ADR-009. */
    public static final SerpFeatureType AI_MODE_ANSWER = of("ai_mode_answer");
    public static final SerpFeatureType FEATURED_SNIPPET = of("featured_snippet");
    public static final SerpFeatureType PEOPLE_ALSO_ASK = of("people_also_ask");
    public static final SerpFeatureType FAQ = of("faq");
    public static final SerpFeatureType RELATED_SEARCHES = of("related_searches");
    public static final SerpFeatureType TOP_STORIES = of("top_stories");
    public static final SerpFeatureType LOCAL_PACK = of("local_pack");
    public static final SerpFeatureType VIDEO_CAROUSEL = of("video_carousel");
    public static final SerpFeatureType IMAGES = of("images");
    public static final SerpFeatureType DISCUSSIONS = of("discussions");
    public static final SerpFeatureType ANSWER_BOX = of("answer_box");
    public static final SerpFeatureType CALCULATOR = of("calculator");
    public static final SerpFeatureType WEATHER = of("weather");

    private final String code;

    private SerpFeatureType(String code) {
        this.code = code;
    }

    public static SerpFeatureType of(String code) {
        return new SerpFeatureType(Codes.normalize(code, "serp feature type"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SerpFeatureType && code.equals(((SerpFeatureType) o).code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
