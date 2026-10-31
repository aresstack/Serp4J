package com.aresstack.serp4j.testkit.fixture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans fixtures for credentials before they are checked in (architecture.md §9, "Fixtures werden vor dem
 * Einchecken anonymisiert und automatisch auf Secret-Muster gescannt").
 *
 * <p>Rules: {@code Authorization}/{@code Proxy-Authorization} values with a scheme, credential-named fields and
 * headers ({@code api_key}, {@code X-Subscription-Token}, {@code password}, {@code client_secret}, …) in JSON,
 * properties, headers and query strings, private-key blocks, and well-known token formats (AWS access keys, GitHub
 * tokens, JWTs). Placeholder values such as {@code ****}, {@code REDACTED}, {@code <api-key>} or {@code ${KEY}} are
 * accepted. Add literal values that must never appear – for example the key of a live recording run – with
 * {@link #forbidding(String...)}.</p>
 *
 * <p>The scanner is a safety net, not a proof of anonymity: it reports the location and a masked hint, never the
 * value.</p>
 */
public final class FixtureSecretScanner {

    private static final String KEY_NAMES = "api[_-]?key|apikey|x-api-key|x-subscription-token|subscription[_-]?token"
            + "|access[_-]?token|refresh[_-]?token|auth[_-]?token|bearer[_-]?token|id[_-]?token|client[_-]?secret"
            + "|secret[_-]?key|private[_-]?key|password|passwd|pwd|secret|token|api[_-]?token|session[_-]?token";

    private static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
            new Rule("authorization-header", Pattern.compile(
                    "(?i)(?<![A-Za-z0-9_-])(?:proxy-)?authorization[\"']?\\s*[:=]\\s*[\"']?\\s*"
                            + "(?:bearer|basic|token|digest|apikey)\\s+([^\\s\"',;]+)"), 1),
            new Rule("credential-field", Pattern.compile(
                    "(?i)(?<![A-Za-z0-9_-])[\"']?(?:" + KEY_NAMES + ")[\"']?\\s*[:=]\\s*[\"']?([^\\s\"',;&}\\]]+)"), 1),
            new Rule("credential-query-parameter", Pattern.compile(
                    "(?i)[?&](?:key|" + KEY_NAMES + ")=([^&\\s\"'#]+)"), 1),
            new Rule("private-key", Pattern.compile("-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----"), 0),
            new Rule("aws-access-key", Pattern.compile("(?<![A-Z0-9])(?:AKIA|ASIA)[0-9A-Z]{16}(?![A-Z0-9])"), 0),
            new Rule("github-token", Pattern.compile("(?<![A-Za-z0-9_])gh[pousr]_[A-Za-z0-9]{36,}"), 0),
            new Rule("jwt", Pattern.compile(
                    "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"), 0)));

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)\\**|x{3,}|\\.{3}|redacted|\\[redacted]|masked|removed|anonymi[sz]ed|<[^>]*>|\\$\\{[^}]*}?|\\{\\{[^}]*(?:}})?"
                    + "|%[a-z_]+%|null|none|true|false|changeme|example|dummy|test|placeholder|your[_-]?[a-z_-]*"
                    + "|\\d{1,3}");

    private final Set<String> forbidden;

    private FixtureSecretScanner(Set<String> forbidden) {
        this.forbidden = forbidden;
    }

    /** Scanner with the built-in rules. */
    public static FixtureSecretScanner defaults() {
        return new FixtureSecretScanner(Collections.<String>emptySet());
    }

    /** The same scanner that additionally reports every occurrence of the given literal values. */
    public FixtureSecretScanner forbidding(String... values) {
        Set<String> all = new LinkedHashSet<String>(forbidden);
        for (String value : values) {
            if (value != null && value.length() >= 4) {
                all.add(value);
            }
        }
        return new FixtureSecretScanner(Collections.unmodifiableSet(all));
    }

    /** Findings in {@code text}; {@code location} labels them. */
    public List<SecretFinding> scan(String location, String text) {
        List<SecretFinding> findings = new ArrayList<SecretFinding>();
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Rule rule : RULES) {
                Matcher matcher = rule.pattern.matcher(line);
                while (matcher.find()) {
                    String value = rule.valueGroup > 0 ? matcher.group(rule.valueGroup) : matcher.group();
                    if (rule.valueGroup > 0 && isPlaceholder(value)) {
                        continue;
                    }
                    findings.add(new SecretFinding(location, i + 1, rule.name, mask(matcher.group(), value)));
                }
            }
            for (String value : forbidden) {
                if (line.contains(value)) {
                    findings.add(new SecretFinding(location, i + 1, "forbidden-value", mask(value, value)));
                }
            }
        }
        return Collections.unmodifiableList(findings);
    }

    /** Findings in a fixture (content decoded as UTF-8). */
    public List<SecretFinding> scan(Fixture fixture) {
        return scan(fixture.getResourcePath(), fixture.getText());
    }

    /**
     * Fails if any of the fixtures contains a suspected secret.
     *
     * @throws AssertionError listing all findings with masked hints
     */
    public void assertClean(Collection<Fixture> fixtures) {
        List<SecretFinding> findings = new ArrayList<SecretFinding>();
        for (Fixture fixture : fixtures) {
            findings.addAll(scan(fixture));
        }
        if (!findings.isEmpty()) {
            StringBuilder message = new StringBuilder("suspected secrets in fixtures (anonymize before check-in):");
            for (SecretFinding finding : findings) {
                message.append("\n  ").append(finding);
            }
            throw new AssertionError(message.toString());
        }
    }

    /** Like {@link #assertClean(Collection)} for single fixtures. */
    public void assertClean(Fixture... fixtures) {
        assertClean(Arrays.asList(fixtures));
    }

    static boolean isPlaceholder(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || PLACEHOLDER.matcher(trimmed).matches()) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.contains("redacted") || lower.contains("****") || lower.contains("masked");
    }

    private static String mask(String context, String value) {
        String shown = value.length() <= 4 ? "****" : value.substring(0, 2) + "****";
        String masked = context.replace(value, shown);
        return masked.length() > 80 ? masked.substring(0, 80) + "…" : masked;
    }

    private static final class Rule {
        private final String name;
        private final Pattern pattern;
        private final int valueGroup;

        Rule(String name, Pattern pattern, int valueGroup) {
            this.name = name;
            this.pattern = pattern;
            this.valueGroup = valueGroup;
        }
    }
}
