package com.aresstack.serp4j.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Navigation allowlist of a session (WP3 §9). Only {@code http} and {@code https} URLs can be allowed;
 * {@code about:blank} is always allowed, {@code file:}, {@code data:}, {@code javascript:} and every other scheme
 * never. Hosts are matched case-insensitively against glob patterns: a leading {@code *.} stands for one or more
 * subdomain labels ({@code *.google.com} matches {@code www.google.com}, not the apex {@code google.com}), every
 * other {@code *} for exactly one label ({@code google.*} matches {@code google.de} but neither
 * {@code google.co.uk} nor {@code google.com.evil.example}). Immutable.
 */
public final class UrlPolicy {

    private static final UrlPolicy ALL_WEB = new UrlPolicy(Collections.<String>emptyList(), true);
    private static final String ABOUT_BLANK = "about:blank";

    private final List<String> hostPatterns;
    private final List<Pattern> compiled;
    private final boolean allowAllHosts;

    private UrlPolicy(List<String> hostPatterns, boolean allowAllHosts) {
        this.hostPatterns = Collections.unmodifiableList(new ArrayList<String>(hostPatterns));
        this.allowAllHosts = allowAllHosts;
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String pattern : hostPatterns) {
            patterns.add(toRegex(pattern));
        }
        this.compiled = Collections.unmodifiableList(patterns);
    }

    /**
     * @return a policy that allows every {@code http} and {@code https} URL (the default)
     */
    public static UrlPolicy allowAllWeb() {
        return ALL_WEB;
    }

    /**
     * @param hostPatterns at least one host glob pattern
     * @return a policy that allows {@code http} and {@code https} URLs on matching hosts only
     */
    public static UrlPolicy allowHosts(String... hostPatterns) {
        Args.notNull(hostPatterns, "hostPatterns");
        List<String> normalized = new ArrayList<String>();
        for (String pattern : hostPatterns) {
            String trimmed = Args.notBlank(pattern, "hostPattern").trim().toLowerCase(Locale.ROOT);
            if (trimmed.contains("/") || trimmed.contains(":")) {
                throw new IllegalArgumentException("host pattern must be a host name glob, not a URL: " + pattern);
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("at least one host pattern is required");
        }
        return new UrlPolicy(normalized, false);
    }

    /**
     * @return whether every web host is allowed
     */
    public boolean isAllowAllHosts() {
        return allowAllHosts;
    }

    /**
     * @return the host patterns; empty for {@link #allowAllWeb()}
     */
    public List<String> getHostPatterns() {
        return hostPatterns;
    }

    /**
     * @param url absolute URL
     * @return whether a navigation to the URL is allowed
     */
    public boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        if (ABOUT_BLANK.equalsIgnoreCase(trimmed)) {
            return true;
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (allowAllHosts) {
            return true;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        for (Pattern pattern : compiled) {
            if (pattern.matcher(normalizedHost).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        String rest = glob;
        if (rest.startsWith("*.")) {
            regex.append("(?:[^.]+\\.)+");
            rest = rest.substring(2);
        }
        String[] parts = rest.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("[^.]+");
            }
            if (!parts[i].isEmpty()) {
                regex.append(Pattern.quote(parts[i]));
            }
        }
        return Pattern.compile(regex.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UrlPolicy)) {
            return false;
        }
        UrlPolicy that = (UrlPolicy) o;
        return allowAllHosts == that.allowAllHosts && hostPatterns.equals(that.hostPatterns);
    }

    @Override
    public int hashCode() {
        return 31 * hostPatterns.hashCode() + (allowAllHosts ? 1 : 0);
    }

    @Override
    public String toString() {
        return allowAllHosts ? "UrlPolicy{allowAllWeb}" : "UrlPolicy{hosts=" + hostPatterns + '}';
    }
}
