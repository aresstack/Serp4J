package com.aresstack.serp4j.browser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * A page script for {@link BrowserSession#evaluate(PageScript)}: a JavaScript function expression such as
 * {@code (arg) => ({title: document.title})}, called with an optional {@link BrowserValue} argument. Extraction
 * scripts are versioned data, not Java code (WP3 §7.2): engine adapters load them with
 * {@link #fromResource(String, Class, String)} and map the returned raw tree in Java. Immutable.
 */
public final class PageScript {

    private final String id;
    private final String source;
    private final BrowserValue argument;

    private PageScript(String id, String source, BrowserValue argument) {
        this.id = Args.notBlank(id, "id").trim();
        this.source = Args.notBlank(source, "source");
        this.argument = Args.notNull(argument, "argument");
    }

    /**
     * @param id     stable id including a version, for example {@code google/serp-v1}; used by test doubles and in
     *               error messages
     * @param source JavaScript function expression
     * @return the script without argument
     */
    public static PageScript of(String id, String source) {
        return new PageScript(id, source, BrowserValue.nullValue());
    }

    /**
     * Loads the source of a script from a UTF-8 class path resource.
     *
     * @param id       script id
     * @param anchor   class whose class loader and package resolve the resource
     * @param resource resource name, relative to the package of {@code anchor} or absolute with a leading {@code /}
     * @return the script
     * @throws IllegalArgumentException if the resource does not exist or cannot be read
     */
    public static PageScript fromResource(String id, Class<?> anchor, String resource) {
        Args.notNull(anchor, "anchor");
        Args.notBlank(resource, "resource");
        InputStream in = anchor.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("script resource not found: " + resource);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return of(id, new String(out.toByteArray(), Charset.forName("UTF-8")));
        } catch (IOException e) {
            throw new IllegalArgumentException("script resource not readable: " + resource, e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    /**
     * @param argument argument passed to the function
     * @return a copy with the argument
     */
    public PageScript withArgument(BrowserValue argument) {
        return new PageScript(id, source, argument);
    }

    /**
     * @return the script id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the JavaScript function expression
     */
    public String getSource() {
        return source;
    }

    /**
     * @return the argument; {@link BrowserValue#nullValue()} if none
     */
    public BrowserValue getArgument() {
        return argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageScript)) {
            return false;
        }
        PageScript that = (PageScript) o;
        return id.equals(that.id) && source.equals(that.source) && argument.equals(that.argument);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * id.hashCode() + source.hashCode()) + argument.hashCode();
    }

    /** Shows id and size only, never the source or the argument. */
    @Override
    public String toString() {
        return "PageScript{" + id + ", " + source.length() + " chars}";
    }
}
