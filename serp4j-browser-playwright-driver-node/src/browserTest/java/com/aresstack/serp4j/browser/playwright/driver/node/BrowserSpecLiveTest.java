package com.aresstack.serp4j.browser.playwright.driver.node;

import com.aresstack.serp4j.browser.BrowserDiscovery;
import com.aresstack.serp4j.browser.BrowserInstallation;
import com.aresstack.serp4j.browser.BrowserProduct;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimes;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserSpec;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.NavigationResult;
import com.aresstack.serp4j.browser.PageSnapshot;
import com.aresstack.serp4j.browser.SnapshotOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * ADR-020 live check (only with {@code -Pserp4j.browserTests=true}): lists the browsers {@link BrowserDiscovery}
 * finds on this machine and starts each one through {@link BrowserRuntimes#create(BrowserSpec)}, headless, with a
 * navigation to {@code about:blank}. A stock Firefox is listed but not started (no factory supports it). Nothing is
 * downloaded; a machine without any detected browser skips.
 */
class BrowserSpecLiveTest {

    @Test
    void everyDetectedBrowserStartsThroughItsFactory() {
        List<BrowserInstallation> found = BrowserDiscovery.system().findAll();
        System.out.println("[adr-020] detected: " + found);
        assumeFalse(found.isEmpty(), "no browser detected on this machine");

        List<String> failures = new ArrayList<String>();
        for (BrowserInstallation installation : found) {
            if (BrowserProduct.FIREFOX.equals(installation.getProduct())) {
                System.out.println("[adr-020] " + installation + ": listed only (stock Firefox is not driven)");
                continue;
            }
            BrowserSpec spec = BrowserSpec.of(installation).build();
            try (BrowserRuntime runtime = BrowserRuntimes.create(spec)) {
                BrowserReadiness readiness = runtime.probe();
                System.out.println("[adr-020] " + spec + " -> " + runtime.getInfo().getBackendId() + " "
                        + readiness);
                if (readiness.getStatus() != BrowserReadiness.Status.READY) {
                    failures.add(spec + ": " + readiness);
                    continue;
                }
                try (BrowserSession session = runtime.openSession(BrowserSessionOptions.defaults())) {
                    NavigationResult navigation = session.navigate(NavigationRequest.to("about:blank"));
                    PageSnapshot snapshot = session.snapshot(SnapshotOptions.urlAndTitle());
                    assertEquals("about:blank", navigation.getFinalUrl());
                    System.out.println("[adr-020] " + installation.getProduct() + " version "
                            + runtime.getInfo().getBrowserVersion().orElse("?") + ", title='"
                            + snapshot.getTitle() + "'");
                }
            } catch (RuntimeException e) {
                failures.add(spec + ": " + e);
            }
        }
        if (!failures.isEmpty()) {
            fail("browsers that did not start: " + failures);
        }
    }
}
