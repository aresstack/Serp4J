package com.aresstack.serp4j.browser;

import java.util.Optional;

/**
 * Backend-neutral element selector: CSS or ARIA role with accessible name (WP3 §5.2). No backend-specific locator
 * syntax. Immutable.
 */
public final class ElementSelector {

    /** Kind of selector. */
    public enum Kind {
        /** A CSS selector. */
        CSS,
        /** An ARIA role, optionally with an exact accessible name. */
        ROLE
    }

    private final Kind kind;
    private final String value;
    private final String accessibleName;

    private ElementSelector(Kind kind, String value, String accessibleName) {
        this.kind = kind;
        this.value = Args.notBlank(value, "value").trim();
        this.accessibleName = accessibleName;
    }

    /**
     * @param css CSS selector
     * @return the selector
     */
    public static ElementSelector css(String css) {
        return new ElementSelector(Kind.CSS, css, null);
    }

    /**
     * @param role           ARIA role such as {@code button}, {@code textbox}, {@code link}
     * @param accessibleName exact accessible name, or {@code null} for any
     * @return the selector
     */
    public static ElementSelector role(String role, String accessibleName) {
        return new ElementSelector(Kind.ROLE, role.toLowerCase(java.util.Locale.ROOT), accessibleName);
    }

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the CSS selector or the role
     */
    public String getValue() {
        return value;
    }

    /**
     * @return the accessible name of a {@link Kind#ROLE} selector; empty for any name or for CSS
     */
    public Optional<String> getAccessibleName() {
        return Optional.ofNullable(accessibleName);
    }

    /**
     * @return whether this is a CSS selector
     */
    public boolean isCss() {
        return kind == Kind.CSS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElementSelector)) {
            return false;
        }
        ElementSelector that = (ElementSelector) o;
        return kind == that.kind && value.equals(that.value)
                && (accessibleName == null ? that.accessibleName == null : accessibleName.equals(that.accessibleName));
    }

    @Override
    public int hashCode() {
        return 31 * (31 * kind.hashCode() + value.hashCode()) + (accessibleName == null ? 0 : accessibleName.hashCode());
    }

    @Override
    public String toString() {
        return kind == Kind.CSS ? "css(" + value + ")"
                : "role(" + value + (accessibleName == null ? "" : ", \"" + accessibleName + "\"") + ")";
    }
}
