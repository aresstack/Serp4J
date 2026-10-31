package com.aresstack.serp4j.testkit.fixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads fixtures from the classpath (layout and sidecar format: see the package documentation).
 *
 * <pre>{@code
 * Fixture organic = Fixtures.loadRecorded("dataforseo", "organic-advanced.json");   // E-1: recorded only
 * http.on(RequestMatcher.post(endpoint)).reply(FakeHttpStep.fixture(organic));
 * }</pre>
 */
public final class Fixtures {

    /** Classpath root of all fixtures. */
    public static final String ROOT = "fixtures";

    /** Suffix of the mandatory sidecar file. */
    public static final String SIDECAR_SUFFIX = ".fixture.properties";

    private Fixtures() {
    }

    /**
     * Loads {@code fixtures/<provider>/<name>} with the context class loader.
     *
     * @throws IllegalArgumentException if the fixture or its sidecar is missing or the sidecar is invalid
     */
    public static Fixture load(String provider, String name) {
        return load(provider, name, defaultClassLoader());
    }

    /** Like {@link #load(String, String)} with an explicit class loader. */
    public static Fixture load(String provider, String name, ClassLoader classLoader) {
        requireSegment(provider, "provider");
        requireName(name);
        String path = ROOT + "/" + provider + "/" + name;
        byte[] content = read(classLoader, path);
        if (content == null) {
            throw new IllegalArgumentException("fixture not found on the classpath: " + path);
        }
        byte[] sidecar = read(classLoader, path + SIDECAR_SUFFIX);
        if (sidecar == null) {
            throw new IllegalArgumentException("fixture " + path + " has no sidecar " + name + SIDECAR_SUFFIX
                    + "; every fixture must declare its provenance (decision E-1)");
        }
        return parse(provider, name, content, sidecar);
    }

    /**
     * Loads a fixture and requires it to be recorded from the real provider – use this in response-mapping tests.
     *
     * @throws IllegalArgumentException if the fixture is synthetic (decision E-1) or cannot be loaded
     */
    public static Fixture loadRecorded(String provider, String name) {
        Fixture fixture = load(provider, name);
        if (!fixture.isRecorded()) {
            throw new IllegalArgumentException("fixture " + fixture.getResourcePath() + " is synthetic; response"
                    + " mappers are tested against recorded provider answers only (decision E-1)");
        }
        return fixture;
    }

    /** All fixtures of {@code provider} visible to the context class loader, sorted by name. */
    public static List<Fixture> list(String provider) {
        requireSegment(provider, "provider");
        List<Fixture> result = new ArrayList<Fixture>();
        for (Fixture fixture : catalog().getFixtures()) {
            if (fixture.getProvider().equals(provider)) {
                result.add(fixture);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Every fixture visible to the context class loader, plus files that break the layout rules. */
    public static FixtureCatalog catalog() {
        return catalog(defaultClassLoader());
    }

    /** Like {@link #catalog()} with an explicit class loader. */
    public static FixtureCatalog catalog(ClassLoader classLoader) {
        TreeSet<String> files = new TreeSet<String>();
        try {
            Enumeration<URL> roots = classLoader.getResources(ROOT);
            while (roots.hasMoreElements()) {
                collect(roots.nextElement(), files);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + ROOT + " on the classpath", e);
        }
        List<Fixture> fixtures = new ArrayList<Fixture>();
        List<String> problems = new ArrayList<String>();
        for (String file : files) {
            String relative = file.substring(ROOT.length() + 1);
            int slash = relative.indexOf('/');
            String fileName = relative.substring(relative.lastIndexOf('/') + 1);
            if (fileName.startsWith("README") || fileName.startsWith(".")) {
                continue;
            }
            if (file.endsWith(SIDECAR_SUFFIX)) {
                String data = file.substring(0, file.length() - SIDECAR_SUFFIX.length());
                if (!files.contains(data)) {
                    problems.add(file + ": sidecar without fixture");
                }
                continue;
            }
            if (slash < 0 || relative.indexOf('/', slash + 1) >= 0) {
                problems.add(file + ": fixtures live exactly at " + ROOT + "/<provider>/<name>");
                continue;
            }
            try {
                fixtures.add(load(relative.substring(0, slash), relative.substring(slash + 1), classLoader));
            } catch (RuntimeException e) {
                problems.add(file + ": " + e.getMessage());
            }
        }
        return new FixtureCatalog(fixtures, problems);
    }

    // ----- parsing -----

    private static Fixture parse(String provider, String name, byte[] content, byte[] sidecarBytes) {
        String location = ROOT + "/" + provider + "/" + name + SIDECAR_SUFFIX;
        Properties sidecar = new Properties();
        try (Reader reader = new InputStreamReader(new java.io.ByteArrayInputStream(sidecarBytes),
                StandardCharsets.UTF_8)) {
            sidecar.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException(location + ": unreadable", e);
        }
        String provenanceText = text(sidecar, "provenance");
        if (provenanceText == null) {
            throw new IllegalArgumentException(location + ": 'provenance' (recorded|synthetic) is required");
        }
        FixtureProvenance provenance;
        try {
            provenance = FixtureProvenance.valueOf(provenanceText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(location + ": provenance must be recorded or synthetic, was '"
                    + provenanceText + "'", e);
        }
        String source = text(sidecar, "source");
        String description = text(sidecar, "description");
        boolean anonymized = Boolean.parseBoolean(text(sidecar, "anonymized"));
        if (provenance == FixtureProvenance.RECORDED) {
            if (source == null) {
                throw new IllegalArgumentException(location + ": recorded fixtures need 'source'");
            }
            if (!anonymized) {
                throw new IllegalArgumentException(location + ": recorded fixtures must be anonymized"
                        + " (anonymized=true) before they are checked in");
            }
        } else if (description == null) {
            throw new IllegalArgumentException(location + ": synthetic fixtures need 'description' saying why"
                    + " they are not recorded");
        }
        String contentType = text(sidecar, "contentType");
        String statusText = text(sidecar, "status");
        int status;
        try {
            status = statusText == null ? 200 : Integer.parseInt(statusText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(location + ": status must be a number, was '" + statusText + "'", e);
        }
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException(location + ": status must be 100..599, was " + status);
        }
        return new Fixture(provider, name, content, provenance, source, text(sidecar, "recordedAt"), anonymized,
                description, contentType != null ? contentType : contentTypeOf(name), status);
    }

    private static String text(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    static String contentTypeOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml; charset=utf-8";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }

    // ----- classpath access -----

    private static ClassLoader defaultClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : Fixtures.class.getClassLoader();
    }

    private static byte[] read(ClassLoader classLoader, String path) {
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    private static void collect(URL root, final TreeSet<String> files) throws IOException {
        if ("file".equals(root.getProtocol())) {
            final Path base;
            try {
                base = Paths.get(root.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("bad classpath URL " + root, e);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile).forEach(file -> files.add(
                        ROOT + "/" + base.relativize(file).toString().replace('\\', '/')));
            }
        } else if ("jar".equals(root.getProtocol())) {
            URLConnection connection = root.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = ((JarURLConnection) connection).getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().startsWith(ROOT + "/")) {
                        files.add(entry.getName());
                    }
                }
            }
        }
    }

    private static void requireSegment(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException(name + " must be a single path segment: '" + value + "'");
        }
    }

    private static void requireName(String name) {
        requireSegment(name, "name");
        if (name.endsWith(SIDECAR_SUFFIX)) {
            throw new IllegalArgumentException("name must be the fixture, not its sidecar: " + name);
        }
    }
}
