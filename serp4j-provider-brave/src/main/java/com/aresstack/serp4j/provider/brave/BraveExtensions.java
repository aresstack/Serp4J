package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.ext.ExtensionKey;

/**
 * Extension keys the Brave adapter sets (namespace {@value #NAMESPACE}). Everything else Brave returns stays
 * available as {@code Extensions.getUnmapped("brave")} (ADR-019, principle 5).
 */
public final class BraveExtensions {

    public static final String NAMESPACE = "brave";

    // Response metadata (query object and top-level hints)
    public static final ExtensionKey<Boolean> IS_NAVIGATIONAL = ExtensionKey.of(NAMESPACE, "isNavigational",
            Boolean.class);
    public static final ExtensionKey<Boolean> IS_NEWS_BREAKING = ExtensionKey.of(NAMESPACE, "isNewsBreaking",
            Boolean.class);
    public static final ExtensionKey<Boolean> IS_GEOLOCAL = ExtensionKey.of(NAMESPACE, "isGeolocal", Boolean.class);
    public static final ExtensionKey<Boolean> BAD_RESULTS = ExtensionKey.of(NAMESPACE, "badResults", Boolean.class);
    public static final ExtensionKey<String> RESPONSE_COUNTRY = ExtensionKey.of(NAMESPACE, "country", String.class);
    /** Key for the summarizer, only with {@code summary=true} (Brave reference §3.4). */
    public static final ExtensionKey<String> SUMMARIZER_KEY = ExtensionKey.of(NAMESPACE, "summarizerKey",
            String.class);
    /** Callback key for Rich Search, only with {@code enable_rich_callback} (not requested by this provider). */
    public static final ExtensionKey<String> RICH_CALLBACK_KEY = ExtensionKey.of(NAMESPACE, "richCallbackKey",
            String.class);

    // Result items
    /** Brave's human-readable age, e.g. "2 days ago". */
    public static final ExtensionKey<String> AGE = ExtensionKey.of(NAMESPACE, "age", String.class);
    /** Result subtype, default {@code generic}. */
    public static final ExtensionKey<String> SUBTYPE = ExtensionKey.of(NAMESPACE, "subtype", String.class);
    public static final ExtensionKey<Boolean> FAMILY_FRIENDLY = ExtensionKey.of(NAMESPACE, "familyFriendly",
            Boolean.class);

    private BraveExtensions() {
    }
}
