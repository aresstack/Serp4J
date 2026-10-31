package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Codes;

/** Key of a numeric metric in {@link SearchMetrics}. Open value type (ADR-004) with constants for common metrics. */
public final class MetricKey {

    public static final MetricKey RESULT_COUNT = of("result_count");
    public static final MetricKey ORGANIC_COUNT = of("organic_count");
    public static final MetricKey PAID_COUNT = of("paid_count");
    public static final MetricKey FEATURE_COUNT = of("feature_count");
    public static final MetricKey DISTINCT_DOMAINS = of("distinct_domains");
    public static final MetricKey DUPLICATE_URLS = of("duplicate_urls");

    private final String code;

    private MetricKey(String code) {
        this.code = code;
    }

    public static MetricKey of(String code) {
        return new MetricKey(Codes.normalize(code, "metric key"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MetricKey && code.equals(((MetricKey) o).code));
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
