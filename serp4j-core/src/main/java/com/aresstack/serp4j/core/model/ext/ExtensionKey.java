package com.aresstack.serp4j.core.model.ext;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed key of a provider- or module-specific extension value (ADR-006).
 *
 * <p>Keys are published by the module that knows the value, for example
 * {@code BraveExtensions.PROFILE = ExtensionKey.of("brave", "profile", BraveProfile.class)}. The namespace is
 * the provider code or module name in the code format of ADR-004; the name may use letters, digits,
 * {@code .}, {@code _} and {@code -}. Two keys are equal when namespace, name and type are equal.</p>
 *
 * @param <T> type of the value stored under this key; values must be immutable
 */
public final class ExtensionKey<T> {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final String namespace;
    private final String name;
    private final Class<T> type;

    private ExtensionKey(String namespace, String name, Class<T> type) {
        this.namespace = namespace;
        this.name = name;
        this.type = type;
    }

    public static <T> ExtensionKey<T> of(String namespace, String name, Class<T> type) {
        String ns = Codes.normalize(namespace, "extension namespace");
        String n = Preconditions.requireText(name, "extension name");
        if (!NAME_PATTERN.matcher(n).matches()) {
            throw new IllegalArgumentException(
                    "extension name must match " + NAME_PATTERN.pattern() + " but was '" + name + "'");
        }
        return new ExtensionKey<T>(ns, n, Preconditions.requireNonNull(type, "extension type"));
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    /** Casts {@code value} to the key's type. */
    public T cast(Object value) {
        return type.cast(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtensionKey)) {
            return false;
        }
        ExtensionKey<?> that = (ExtensionKey<?>) o;
        return namespace.equals(that.namespace) && name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, type);
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
