package com.aresstack.serp4j.browser;

import java.nio.file.Path;

/** A browser found by {@link BrowserDiscovery} (ADR-020). Immutable. */
public final class BrowserInstallation {

    /** How the browser was found. */
    public enum Origin {
        /** System or per-user installation at a well-known location. */
        INSTALLED,
        /** Portable browser at a well-known cache location, for example Camoufox. */
        PORTABLE
    }

    private final BrowserProduct product;
    private final Path executable;
    private final Origin origin;

    public BrowserInstallation(BrowserProduct product, Path executable, Origin origin) {
        this.product = Args.notNull(product, "product");
        this.executable = Args.notNull(executable, "executable");
        this.origin = Args.notNull(origin, "origin");
    }

    public BrowserProduct getProduct() {
        return product;
    }

    public Path getExecutable() {
        return executable;
    }

    public Origin getOrigin() {
        return origin;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BrowserInstallation)) {
            return false;
        }
        BrowserInstallation that = (BrowserInstallation) o;
        return product.equals(that.product) && executable.equals(that.executable) && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return (product.hashCode() * 31 + executable.hashCode()) * 31 + origin.hashCode();
    }

    @Override
    public String toString() {
        return product + "@" + executable + " (" + origin + ")";
    }
}
