package com.aresstack.serp4j.browser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds installed and portable browsers at well-known locations without starting anything (ADR-020). No registry
 * queries, no process calls: only file existence checks, so the result is deterministic and testable with
 * {@link #of(Os, Map, FileCheck)}.
 *
 * <p>The list is a convenience for hosts that want to offer a choice; the host still decides and passes a
 * {@link BrowserSpec}. A stock Firefox is reported, but only Playwright's Firefox build or Camoufox can be driven.</p>
 */
public final class BrowserDiscovery {

    /** Operating system family. */
    public enum Os {
        WINDOWS, MAC, LINUX;

        /** @return the family of the running JVM */
        public static Os current() {
            String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (name.startsWith("windows")) {
                return WINDOWS;
            }
            if (name.startsWith("mac") || name.startsWith("darwin")) {
                return MAC;
            }
            return LINUX;
        }
    }

    /** File existence check, replaceable in tests. */
    public interface FileCheck {
        boolean isExecutableFile(Path path);
    }

    private final Os os;
    private final Map<String, String> env;
    private final FileCheck files;

    private BrowserDiscovery(Os os, Map<String, String> env, FileCheck files) {
        this.os = Args.notNull(os, "os");
        this.env = Args.notNull(env, "env");
        this.files = Args.notNull(files, "files");
    }

    /** @return discovery for the running system */
    public static BrowserDiscovery system() {
        return new BrowserDiscovery(Os.current(), System.getenv(), new FileCheck() {
            @Override
            public boolean isExecutableFile(Path path) {
                return Files.isRegularFile(path);
            }
        });
    }

    /**
     * @param os    operating system
     * @param env   environment variables
     * @param files file check
     * @return discovery with injected system access
     */
    public static BrowserDiscovery of(Os os, Map<String, String> env, FileCheck files) {
        return new BrowserDiscovery(os, env, files);
    }

    /** @return all browsers found, in the order Edge, Chrome, Chromium, Firefox, Camoufox */
    public List<BrowserInstallation> findAll() {
        List<BrowserInstallation> result = new ArrayList<BrowserInstallation>();
        for (BrowserProduct p : new BrowserProduct[] {BrowserProduct.EDGE, BrowserProduct.CHROME,
                BrowserProduct.CHROMIUM, BrowserProduct.FIREFOX, BrowserProduct.CAMOUFOX}) {
            Optional<BrowserInstallation> found = find(p);
            if (found.isPresent()) {
                result.add(found.get());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * @param product product to look for
     * @return the first existing candidate
     */
    public Optional<BrowserInstallation> find(BrowserProduct product) {
        Args.notNull(product, "product");
        BrowserInstallation.Origin origin = BrowserProduct.CAMOUFOX.equals(product)
                ? BrowserInstallation.Origin.PORTABLE : BrowserInstallation.Origin.INSTALLED;
        for (Path candidate : candidates(product)) {
            if (files.isExecutableFile(candidate)) {
                return Optional.of(new BrowserInstallation(product, candidate, origin));
            }
        }
        return Optional.empty();
    }

    /**
     * @param product product
     * @return candidate paths in lookup order (visible for diagnostics)
     */
    public List<Path> candidates(BrowserProduct product) {
        Set<Path> c = new LinkedHashSet<Path>();
        switch (os) {
            case WINDOWS:
                windows(product, c);
                break;
            case MAC:
                mac(product, c);
                break;
            default:
                linux(product, c);
                break;
        }
        return new ArrayList<Path>(c);
    }

    private void windows(BrowserProduct p, Set<Path> c) {
        List<String> roots = new ArrayList<String>();
        for (String var : new String[] {"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
            String v = env.get(var);
            if (v != null && !v.trim().isEmpty()) {
                roots.add(v.trim());
            }
        }
        String[] rel;
        if (BrowserProduct.EDGE.equals(p)) {
            rel = new String[] {"Microsoft\\Edge\\Application\\msedge.exe"};
        } else if (BrowserProduct.CHROME.equals(p)) {
            rel = new String[] {"Google\\Chrome\\Application\\chrome.exe"};
        } else if (BrowserProduct.CHROMIUM.equals(p)) {
            rel = new String[] {"Chromium\\Application\\chrome.exe"};
        } else if (BrowserProduct.FIREFOX.equals(p)) {
            rel = new String[] {"Mozilla Firefox\\firefox.exe"};
        } else if (BrowserProduct.CAMOUFOX.equals(p)) {
            rel = new String[] {"camoufox\\camoufox.exe", "camoufox\\camoufox\\Cache\\camoufox.exe"};
        } else {
            return;
        }
        for (String root : roots) {
            for (String r : rel) {
                c.add(Paths.get(root + "\\" + r));
            }
        }
    }

    private void mac(BrowserProduct p, Set<Path> c) {
        String home = env.get("HOME");
        String app;
        if (BrowserProduct.EDGE.equals(p)) {
            app = "Microsoft Edge.app/Contents/MacOS/Microsoft Edge";
        } else if (BrowserProduct.CHROME.equals(p)) {
            app = "Google Chrome.app/Contents/MacOS/Google Chrome";
        } else if (BrowserProduct.CHROMIUM.equals(p)) {
            app = "Chromium.app/Contents/MacOS/Chromium";
        } else if (BrowserProduct.FIREFOX.equals(p)) {
            app = "Firefox.app/Contents/MacOS/firefox";
        } else if (BrowserProduct.CAMOUFOX.equals(p)) {
            if (home != null) {
                c.add(Paths.get(home, "Library/Caches/camoufox/Camoufox.app/Contents/MacOS/camoufox"));
            }
            return;
        } else {
            return;
        }
        c.add(Paths.get("/Applications", app));
        if (home != null) {
            c.add(Paths.get(home, "Applications", app));
        }
    }

    private void linux(BrowserProduct p, Set<Path> c) {
        String[] names;
        if (BrowserProduct.EDGE.equals(p)) {
            names = new String[] {"microsoft-edge", "microsoft-edge-stable"};
            c.add(Paths.get("/opt/microsoft/msedge/msedge"));
        } else if (BrowserProduct.CHROME.equals(p)) {
            names = new String[] {"google-chrome", "google-chrome-stable"};
            c.add(Paths.get("/opt/google/chrome/chrome"));
        } else if (BrowserProduct.CHROMIUM.equals(p)) {
            names = new String[] {"chromium", "chromium-browser"};
            c.add(Paths.get("/snap/bin/chromium"));
        } else if (BrowserProduct.FIREFOX.equals(p)) {
            names = new String[] {"firefox"};
        } else if (BrowserProduct.CAMOUFOX.equals(p)) {
            String home = env.get("HOME");
            if (home != null) {
                c.add(Paths.get(home, ".cache/camoufox/camoufox-bin"));
                c.add(Paths.get(home, ".cache/camoufox/camoufox"));
            }
            names = new String[] {"camoufox"};
        } else {
            return;
        }
        String path = env.get("PATH");
        List<Path> fromPath = new ArrayList<Path>();
        if (path != null) {
            for (String dir : path.split(File.pathSeparator.equals(";") ? ":" : File.pathSeparator)) {
                if (!dir.isEmpty()) {
                    for (String n : names) {
                        fromPath.add(Paths.get(dir, n));
                    }
                }
            }
        }
        Set<Path> ordered = new LinkedHashSet<Path>(fromPath);
        ordered.addAll(c);
        c.clear();
        c.addAll(ordered);
    }
}
