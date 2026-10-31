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
import java.util.TreeSet;

/**
 * Immutable, case-insensitive, multi-valued HTTP headers. {@link #toString()} masks the values of sensitive
 * headers so requests can be logged safely: the well-known credential headers ({@link #isSensitive(String)}) and
 * every header marked with {@link Builder#sensitive(String)}, which {@link RequestAuthentication} does for the
 * header it sets, whatever its name.
 */
public final class HttpHeaders {

    private static final Set<String> SENSITIVE = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key",
            "x-auth-token", "x-access-token", "x-subscription-token", "x-amz-security-token", "x-goog-api-key")));

    private static final HttpHeaders EMPTY = new HttpHeaders(
            Collections.unmodifiableMap(new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)),
            Collections.<String>emptySet());

    private final Map<String, List<String>> values;
    private final Set<String> sensitiveNames;

    private HttpHeaders(Map<String, List<String>> values, Set<String> sensitiveNames) {
        this.values = values;
        this.sensitiveNames = sensitiveNames;
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

    /** Whether a header of this name is a well-known credential header that must never be logged or stored. */
    public static boolean isSensitive(String name) {
        return name != null && SENSITIVE.contains(name.trim().toLowerCase(Locale.ROOT));
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

    /**
     * Whether the header's value is masked in {@link #toString()}: a well-known credential header or one marked
     * with {@link Builder#sensitive(String)}.
     */
    public boolean isSensitiveHeader(String name) {
        return isSensitive(name) || sensitiveNames.contains(requireName(name).toLowerCase(Locale.ROOT));
    }

    /** Lower-cased names marked sensitive for this instance, in addition to the well-known ones. */
    public Set<String> getSensitiveNames() {
        return sensitiveNames;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            builder.values.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        builder.sensitiveNames.addAll(sensitiveNames);
        return builder;
    }

    /**
     * Trims the name and requires it to be an RFC 7230 token: visible ASCII ({@code 0x21}–{@code 0x7E}) without
     * the separators {@code ()<>@,;:\"/[]?={}}. Rejects names that could smuggle a second header or a value.
     */
    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("header name must not be blank");
        }
        String key = name.trim();
        for (int i = 0; i < key.length(); i++) {
            if (!isTokenChar(key.charAt(i))) {
                throw new IllegalArgumentException("header name must be an RFC 7230 token (visible ASCII without "
                        + "separators), got '" + key + "'");
            }
        }
        return key;
    }

    private static boolean isTokenChar(char c) {
        if (c <= 0x20 || c >= 0x7F) {
            return false;
        }
        switch (c) {
            case '(': case ')': case '<': case '>': case '@': case ',': case ';': case ':': case '\\':
            case '"': case '/': case '[': case ']': case '?': case '=': case '{': case '}':
                return false;
            default:
                return true;
        }
    }

    /**
     * Rejects values that would end the header line early (CR, LF, NUL). The message names the header but never
     * the value, because the value may be a credential.
     */
    private static String requireValue(String key, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value of header '" + key + "' must not be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') {
                throw new IllegalArgumentException("value of header '" + key
                        + "' must not contain CR, LF or NUL characters");
            }
        }
        return value;
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
        if (values.size() != that.values.size() || !sensitiveNames.equals(that.sensitiveNames)) {
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
        int hash = sensitiveNames.hashCode();
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
            text.append(isSensitiveHeader(entry.getKey()) ? "[****]" : entry.getValue().toString());
        }
        return text.append('}').toString();
    }

    /** Builder for {@link HttpHeaders}; names are matched case-insensitively, the first spelling is kept. */
    public static final class Builder {
        private final Map<String, List<String>> values = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        private final Set<String> sensitiveNames = new TreeSet<String>();

        private Builder() {
        }

        /**
         * Appends a value.
         *
         * @throws IllegalArgumentException if the name is not an RFC 7230 token or the value contains CR, LF or
         *         NUL; the message names the header, never the value
         */
        public Builder add(String name, String value) {
            String key = requireName(name);
            requireValue(key, value);
            List<String> list = values.get(key);
            if (list == null) {
                list = new ArrayList<String>(1);
                values.put(key, list);
            }
            list.add(value);
            return this;
        }

        /**
         * Replaces all values of the header.
         *
         * @throws IllegalArgumentException if the name is not an RFC 7230 token or the value contains CR, LF or
         *         NUL; the message names the header, never the value
         */
        public Builder set(String name, String value) {
            remove(name);
            return add(name, value);
        }

        /** Removes the header; a sensitivity marking for the name stays. */
        public Builder remove(String name) {
            values.remove(requireName(name));
            return this;
        }

        /**
         * Marks the header (whatever its name) as carrying a credential, so its value is masked in
         * {@link HttpHeaders#toString()}. The marking survives {@link #build()} and {@link HttpHeaders#toBuilder()}.
         */
        public Builder sensitive(String name) {
            sensitiveNames.add(requireName(name).toLowerCase(Locale.ROOT));
            return this;
        }

        /** Adds all values and sensitivity markings of {@code headers}. */
        public Builder addAll(HttpHeaders headers) {
            if (headers == null) {
                throw new IllegalArgumentException("headers must not be null");
            }
            for (Map.Entry<String, List<String>> entry : headers.values.entrySet()) {
                for (String value : entry.getValue()) {
                    add(entry.getKey(), value);
                }
            }
            sensitiveNames.addAll(headers.sensitiveNames);
            return this;
        }

        public HttpHeaders build() {
            if (values.isEmpty() && sensitiveNames.isEmpty()) {
                return EMPTY;
            }
            Map<String, List<String>> copy = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
            }
            return new HttpHeaders(Collections.unmodifiableMap(copy),
                    Collections.unmodifiableSet(new TreeSet<String>(sensitiveNames)));
        }
    }
}
