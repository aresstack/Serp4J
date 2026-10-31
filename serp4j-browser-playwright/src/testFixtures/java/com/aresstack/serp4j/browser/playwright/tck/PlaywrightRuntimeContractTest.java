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
import java.nio.file.Paths;
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

    private TckHttpServer server;

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
    }

    /**
     * @return the builder used for every runtime of the TCK; subclasses may add settings
     */
    protected PlaywrightBrowserRuntime.Builder runtimeBuilder() {
        PlaywrightBrowserRuntime.Builder builder = PlaywrightBrowserRuntime.builder()
                .maxConcurrentSessions(3)
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
