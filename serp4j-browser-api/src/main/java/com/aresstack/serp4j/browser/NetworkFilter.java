package com.aresstack.serp4j.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Selection of observed exchanges (WP3 §5.3). Empty criteria match everything. URL patterns are regular
 * expressions matched partially against the full URL; content types are matched by prefix ignoring parameters and
 * case. Immutable.
 */
public final class NetworkFilter {

    /** Default for {@link #getMaxBodyBytes()}: 2 MiB. */
    public static final int DEFAULT_MAX_BODY_BYTES = 2 * 1024 * 1024;

    private static final NetworkFilter ANY = builder().build();

    private final List<Pattern> urlPatterns;
    private final Set<String> methods;
    private final Set<String> resourceTypes;
    private final Set<String> contentTypes;
    private final boolean captureBody;
    private final int maxBodyBytes;

    private NetworkFilter(Builder builder) {
        this.urlPatterns = Collections.unmodifiableList(new ArrayList<Pattern>(builder.urlPatterns));
        this.methods = Collections.unmodifiableSet(new TreeSet<String>(builder.methods));
        this.resourceTypes = Collections.unmodifiableSet(new TreeSet<String>(builder.resourceTypes));
        this.contentTypes = Collections.unmodifiableSet(new TreeSet<String>(builder.contentTypes));
        this.captureBody = builder.captureBody;
        this.maxBodyBytes = Args.positive(builder.maxBodyBytes, "maxBodyBytes");
    }

    /**
     * @return a filter that matches everything and does not capture bodies
     */
    public static NetworkFilter any() {
        return ANY;
    }

    /**
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Matches the request part. Content types are checked separately with {@link #matchesContentType(String)}
     * because they are known only after the response.
     *
     * @param method       HTTP method
     * @param url          request URL
     * @param resourceType resource type such as {@code document}, {@code xhr}, {@code fetch}, {@code script}
     * @return whether the request matches
     */
    public boolean matchesRequest(String method, String url, String resourceType) {
        if (!methods.isEmpty() && (method == null || !methods.contains(method.toUpperCase(Locale.ROOT)))) {
            return false;
        }
        if (!resourceTypes.isEmpty()
                && (resourceType == null || !resourceTypes.contains(resourceType.toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (urlPatterns.isEmpty()) {
            return true;
        }
        for (Pattern pattern : urlPatterns) {
            if (url != null && pattern.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param contentType value of the {@code Content-Type} response header, possibly {@code null}
     * @return whether the content type matches
     */
    public boolean matchesContentType(String contentType) {
        if (contentTypes.isEmpty()) {
            return true;
        }
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        for (String prefix : contentTypes) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param exchange an observed exchange
     * @return whether the exchange matches all criteria
     */
    public boolean matches(ObservedExchange exchange) {
        return matchesRequest(exchange.getMethod(), exchange.getUrl(), exchange.getResourceType())
                && matchesContentType(exchange.getContentType().orElse(null));
    }

    /**
     * @return URL patterns
     */
    public List<Pattern> getUrlPatterns() {
        return urlPatterns;
    }

    /**
     * @return upper-case HTTP methods
     */
    public Set<String> getMethods() {
        return methods;
    }

    /**
     * @return lower-case resource types
     */
    public Set<String> getResourceTypes() {
        return resourceTypes;
    }

    /**
     * @return lower-case content type prefixes
     */
    public Set<String> getContentTypes() {
        return contentTypes;
    }

    /**
     * @return whether response bodies are captured
     */
    public boolean isCaptureBody() {
        return captureBody;
    }

    /**
     * @return hard limit of a captured body; longer bodies are cut and marked as truncated
     */
    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkFilter)) {
            return false;
        }
        NetworkFilter that = (NetworkFilter) o;
        return captureBody == that.captureBody && maxBodyBytes == that.maxBodyBytes
                && patterns(urlPatterns).equals(patterns(that.urlPatterns)) && methods.equals(that.methods)
                && resourceTypes.equals(that.resourceTypes) && contentTypes.equals(that.contentTypes);
    }

    private static List<String> patterns(List<Pattern> patterns) {
        List<String> result = new ArrayList<String>();
        for (Pattern pattern : patterns) {
            result.add(pattern.pattern());
        }
        return result;
    }

    @Override
    public int hashCode() {
        int result = patterns(urlPatterns).hashCode();
        result = 31 * result + methods.hashCode();
        result = 31 * result + resourceTypes.hashCode();
        result = 31 * result + contentTypes.hashCode();
        result = 31 * result + (captureBody ? 1 : 0);
        return 31 * result + maxBodyBytes;
    }

    @Override
    public String toString() {
        return "NetworkFilter{urls=" + patterns(urlPatterns) + ", methods=" + methods + ", resourceTypes="
                + resourceTypes + ", contentTypes=" + contentTypes + ", captureBody=" + captureBody
                + ", maxBodyBytes=" + maxBodyBytes + '}';
    }

    /** Builder for {@link NetworkFilter}. */
    public static final class Builder {
        private final List<Pattern> urlPatterns = new ArrayList<Pattern>();
        private final Set<String> methods = new TreeSet<String>();
        private final Set<String> resourceTypes = new TreeSet<String>();
        private final Set<String> contentTypes = new TreeSet<String>();
        private boolean captureBody;
        private int maxBodyBytes = DEFAULT_MAX_BODY_BYTES;

        private Builder() {
        }

        /**
         * @param regex regular expression, matched partially against the URL; several patterns are alternatives
         * @return this builder
         */
        public Builder urlMatching(String regex) {
            urlPatterns.add(Pattern.compile(Args.notBlank(regex, "regex")));
            return this;
        }

        /**
         * @param method HTTP method such as {@code GET} or {@code POST}
         * @return this builder
         */
        public Builder method(String method) {
            methods.add(Args.notBlank(method, "method").trim().toUpperCase(Locale.ROOT));
            return this;
        }

        /**
         * @param resourceType resource type such as {@code document}, {@code xhr}, {@code fetch}
         * @return this builder
         */
        public Builder resourceType(String resourceType) {
            resourceTypes.add(Args.notBlank(resourceType, "resourceType").trim().toLowerCase(Locale.ROOT));
            return this;
        }

        /**
         * @param contentTypePrefix content type prefix such as {@code application/json} or {@code text/}
         * @return this builder
         */
        public Builder contentType(String contentTypePrefix) {
            contentTypes.add(Args.notBlank(contentTypePrefix, "contentTypePrefix").trim().toLowerCase(Locale.ROOT));
            return this;
        }

        /**
         * @param captureBody whether response bodies are captured, default {@code false}
         * @return this builder
         */
        public Builder captureBody(boolean captureBody) {
            this.captureBody = captureBody;
            return this;
        }

        /**
         * @param maxBodyBytes positive limit of a captured body
         * @return this builder
         */
        public Builder maxBodyBytes(int maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
            return this;
        }

        /**
         * @return the filter
         */
        public NetworkFilter build() {
            return new NetworkFilter(this);
        }
    }
}
