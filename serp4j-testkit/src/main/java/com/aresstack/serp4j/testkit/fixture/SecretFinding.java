package com.aresstack.serp4j.testkit.fixture;

/** One suspected secret found by {@link FixtureSecretScanner}; the value itself is never stored. Immutable. */
public final class SecretFinding {

    private final String location;
    private final int line;
    private final String rule;
    private final String hint;

    SecretFinding(String location, int line, String rule, String hint) {
        this.location = location;
        this.line = line;
        this.rule = rule;
        this.hint = hint;
    }

    /** Fixture path or other label of the scanned text. */
    public String getLocation() {
        return location;
    }

    /** 1-based line number. */
    public int getLine() {
        return line;
    }

    /** Name of the rule that matched, e.g. {@code credential-field}. */
    public String getRule() {
        return rule;
    }

    /** Masked context, e.g. {@code "api_key": "ab****"}. */
    public String getHint() {
        return hint;
    }

    @Override
    public String toString() {
        return location + ":" + line + " [" + rule + "] " + hint;
    }
}
