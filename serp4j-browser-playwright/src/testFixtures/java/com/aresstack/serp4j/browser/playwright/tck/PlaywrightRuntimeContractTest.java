package com.aresstack.serp4j.browser.playwright.tck;

import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.playwright.PlaywrightBrowserRuntime;
import com.aresstack.serp4j.browser.testkit.BrowserRuntimeContractTest;
import com.aresstack.serp4j.browser.testkit.TckHttpServer;
import com.aresstack.serp4j.browser.testkit.TckSite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The browser TCK for {@link PlaywrightBrowserRuntime}, run by each driver module with its own driver on the class
 * path. Serves the TCK site from a local {@link TckHttpServer}; the browser executable can be given with the
 * environment variable {@value #EXECUTABLE_ENV}. Crash and orphan checks use {@code ProcessHandle} and are
 * skipped on Java 8.
 */
public abstract class PlaywrightRuntimeContractTest extends BrowserRuntimeContractTest {

    /** Environment variable with the browser executable; unset = Playwright's lookup. */
    public static final String EXECUTABLE_ENV = "SERP4J_BROWSER_EXECUTABLE";

    /** Close timeout of the TCK runtimes; short, so that the hard-kill test stays fast. */
    static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private TckHttpServer server;
    private Path profileRoot;

    private synchronized Path profileRoot() {
        if (profileRoot == null) {
            try {
                profileRoot = Files.createTempDirectory("serp4j-tck-profiles");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return profileRoot;
    }

    @Override
    protected final synchronized TckSite site() {
        if (server == null) {
            try {
                server = TckHttpServer.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return server;
    }

    @AfterAll
    final void stopServer() {
        if (server != null) {
            server.close();
        }
        if (profileRoot != null) {
            deleteQuietly(profileRoot.toFile());
        }
    }

    private static void deleteQuietly(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteQuietly(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    /**
     * @return the builder used for every runtime of the TCK; subclasses may add settings
     */
    protected PlaywrightBrowserRuntime.Builder runtimeBuilder() {
        PlaywrightBrowserRuntime.Builder builder = PlaywrightBrowserRuntime.builder()
                .maxConcurrentSessions(3)
                .closeTimeout(CLOSE_TIMEOUT)
                .profileDirectory(profileRoot())
                .shutdownHook(false);
        String executable = System.getenv(EXECUTABLE_ENV);
        if (executable != null && !executable.trim().isEmpty()) {
            builder.executablePath(Paths.get(executable.trim()));
        }
        return builder;
    }

    @Override
    protected final BrowserRuntime newRuntime() {
        return runtimeBuilder().build();
    }

    @Override
    protected Object processSnapshot() {
        return ProcessProbe.isSupported() ? ProcessProbe.descendants() : null;
    }

    /** Stops (SIGSTOP) the browser processes started after {@code before}; Unix only. */
    @Override
    protected boolean freezeBrowser(BrowserRuntime runtime, Object before) throws Exception {
        if (before == null || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return false;
        }
        Map<?, ?> previous = (Map<?, ?>) before;
        List<String> command = new ArrayList<String>();
        command.add("kill");
        command.add("-STOP");
        for (Map.Entry<Long, String> process : ProcessProbe.descendants().entrySet()) {
            String line = process.getValue().toLowerCase(Locale.ROOT);
            boolean browser = line.contains("chrom") || line.contains("headless_shell")
                    || line.contains("firefox") || line.contains("webkit");
            if (!previous.containsKey(process.getKey()) && browser) {
                command.add(String.valueOf(process.getKey()));
            }
        }
        if (command.size() == 2) {
            return false;
        }
        Process kill = new ProcessBuilder(command).redirectErrorStream(true).start();
        return kill.waitFor() == 0;
    }

    @Override
    protected Duration hungCloseBound() {
        return CLOSE_TIMEOUT.plusSeconds(20);
    }

    @Override
    protected boolean crashBrowser(BrowserRuntime runtime, Object before) throws Exception {
        if (before == null) {
            return false;
        }
        Map<?, ?> previous = (Map<?, ?>) before;
        List<Long> killed = new ArrayList<Long>();
        for (Map.Entry<Long, String> process : ProcessProbe.descendants().entrySet()) {
            String command = process.getValue().toLowerCase(Locale.ROOT);
            boolean browser = command.contains("chrom") || command.contains("headless_shell")
                    || command.contains("firefox") || command.contains("webkit");
            if (!previous.containsKey(process.getKey()) && browser) {
                ProcessProbe.kill(process.getKey());
                killed.add(process.getKey());
            }
        }
        return !killed.isEmpty();
    }

    @Override
    protected void assertNoOrphanedProcesses(Object before, Object running) throws Exception {
        if (before == null || running == null) {
            return;
        }
        Map<?, ?> previous = (Map<?, ?>) before;
        Map<?, ?> started = (Map<?, ?>) running;
        long deadline = System.nanoTime() + 15_000_000_000L;
        List<String> alive = new ArrayList<String>();
        do {
            alive.clear();
            for (Map.Entry<?, ?> process : started.entrySet()) {
                long pid = (Long) process.getKey();
                if (!previous.containsKey(process.getKey()) && ProcessProbe.isAlive(pid)) {
                    alive.add(pid + " " + process.getValue());
                }
            }
            if (alive.isEmpty()) {
                return;
            }
            Thread.sleep(200);
        } while (System.nanoTime() < deadline);
        Assertions.fail("processes left behind after close: " + alive);
    }
}
