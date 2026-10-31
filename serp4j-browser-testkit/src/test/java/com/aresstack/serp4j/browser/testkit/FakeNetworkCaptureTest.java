package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.NetworkCapture;
import com.aresstack.serp4j.browser.NetworkFilter;
import com.aresstack.serp4j.browser.ObservedExchange;
import com.aresstack.serp4j.browser.WaitOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeNetworkCaptureTest {

    private static final String PAGE = "https://www.example.test/";

    private static FakeBrowserRuntime runtime() {
        return FakeBrowserRuntime.builder().web(FakeWeb.builder().page(PAGE, FakePage.builder()
                .exchange(FakeExchange.get(PAGE + "slow").delay(Duration.ofMillis(120)).build())
                .exchange(FakeExchange.get(PAGE + "fast").delay(Duration.ofMillis(10)).build())
                .build()).build()).build();
    }

    @Test
    void exchanges_are_returned_in_completion_order() throws Exception {
        try (FakeBrowserRuntime runtime = runtime();
             BrowserSession session = runtime.openSession(BrowserSessionOptions.defaults());
             NetworkCapture capture = session.network().start(NetworkFilter.any())) {
            session.navigate(NavigationRequest.to(PAGE));
            assertThat(capture.awaitQuiet(Duration.ofMillis(20), Duration.ofSeconds(2)))
                    .isEqualTo(WaitOutcome.SATISFIED);
            List<String> urls = new ArrayList<String>();
            for (ObservedExchange exchange : capture.getExchanges()) {
                urls.add(exchange.getUrl());
            }
            assertThat(urls).containsExactly(PAGE + "fast", PAGE + "slow");
        }
    }

    @Test
    void quiet_ignores_exchanges_outside_the_filter() throws Exception {
        try (FakeBrowserRuntime runtime = runtime();
             BrowserSession session = runtime.openSession(BrowserSessionOptions.defaults());
             NetworkCapture capture = session.network().start(
                     NetworkFilter.builder().urlMatching("/nothing-matches$").build())) {
            session.navigate(NavigationRequest.to(PAGE));
            assertThat(capture.awaitQuiet(Duration.ofMillis(10), Duration.ofMillis(60)))
                    .isEqualTo(WaitOutcome.SATISFIED);
        }
    }
}
