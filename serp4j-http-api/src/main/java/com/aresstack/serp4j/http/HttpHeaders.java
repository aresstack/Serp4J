package com.aresstack.serp4j.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable, case-insensitive, multi-valued HTTP headers. {@link #toString()} masks the values of
 * {@link #isSensitive(String) sensitive} headers so requests can be logged safely.
 */
public final class HttpHeaders {

    private static final Set<String> SENSITIVE = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key",
            "x-auth-token", "x-access-token", "x-subscription-token", "x-amz-security-token", "x-goog-api-key")));

    private static final HttpHeaders EMPTY = new HttpHeaders(
            Collections.unmodifiableMap(new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)));

    private final Map<String, List<String>> values;

    private HttpHeaders(Map<String, List<String>> values) {
        this.values = values;
    }

    public static HttpHeaders empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HttpHeaders of(String name, String value) {
        return builder().add(name, value).build();
    }

    /** Whether a header's value is a credential and must never be logged or stored. */
    public static boolean isSensitive(String name) {
        return name != null && SENSITIVE.contains(name.toLowerCase(Locale.ROOT));
    }

    /** First value of the header, case-insensitive. */
    public Optional<String> getFirst(String name) {
        List<String> list = values.get(requireName(name));
        return list == null || list.isEmpty() ? Optional.<String>empty() : Optional.of(list.get(0));
    }

    /** All values of the header in insertion order; empty list when absent. */
    public List<String> getAll(String name) {
        List<String> list = values.get(requireName(name));
        return list == null ? Collections.<String>emptyList() : list;
    }

    public boolean contains(String name) {
        return values.containsKey(requireName(name));
    }

    /** Header names, sorted case-insensitively, in the spelling of their first occurrence. */
    public Set<String> getNames() {
        return values.keySet();
    }

    /** Unmodifiable view, keyed case-insensitively; values are unmodifiable lists. */
    public Map<String, List<String>> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            builder.values.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return builder;
    }

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("header name must not be blank");
        }
        return name.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpHeaders)) {
            return false;
        }
        HttpHeaders that = (HttpHeaders) o;
        if (values.size() != that.values.size()) {
            return false;
        }
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            if (!entry.getValue().equals(that.values.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            hash += Objects.hash(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return hash;
    }

    /** Masks sensitive values. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("{");
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            if (text.length() > 1) {
                text.append(", ");
            }
            text.append(entry.getKey()).append('=');
            text.append(isSensitive(entry.getKey()) ? "[****]" : entry.getValue().toString());
        }
        return text.append('}').toString();
    }

    /** Builder for {@link HttpHeaders}; names are matched case-insensitively, the first spelling is kept. */
    public static final class Builder {
        private final Map<String, List<String>> values = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);

        private Builder() {
        }

        /** Appends a value. */
        public Builder add(String name, String value) {
            String key = requireName(name);
            if (value == null) {
                throw new IllegalArgumentException("value of header '" + key + "' must not be null");
            }
            List<String> list = values.get(key);
            if (list == null) {
                list = new ArrayList<String>(1);
                values.put(key, list);
            }
            list.add(value);
            return this;
        }

        /** Replaces all values of the header. */
        public Builder set(String name, String value) {
            remove(name);
            return add(name, value);
        }

        public Builder remove(String name) {
            values.remove(requireName(name));
            return this;
        }

        public Builder addAll(HttpHeaders headers) {
            if (headers == null) {
                throw new IllegalArgumentException("headers must not be null");
            }
            for (Map.Entry<String, List<String>> entry : headers.values.entrySet()) {
                for (String value : entry.getValue()) {
                    add(entry.getKey(), value);
                }
            }
            return this;
        }

        public HttpHeaders build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            Map<String, List<String>> copy = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
            }
            return new HttpHeaders(Collections.unmodifiableMap(copy));
        }
    }
}
