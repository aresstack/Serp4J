package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserCapability;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.ElementSelector;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.ObstaclePolicy;
import com.aresstack.serp4j.browser.PageObstacle;
import com.aresstack.serp4j.browser.PageObstacleException;
import com.aresstack.serp4j.browser.WaitCondition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The headed variant of the fake, which the TCK (run headless) does not cover. */
class FakeObstacleAndProfileTest {

    private static final String PAGE = "https://www.example.test/";

    @Test
    void headed_fake_waits_for_a_human_until_the_condition_holds_or_the_time_is_up() {
        try (FakeBrowserRuntime runtime = FakeBrowserRuntime.builder()
                .capabilities(EnumSet.of(BrowserCapability.JAVASCRIPT, BrowserCapability.HEADED_MODE))
                .web(FakeWeb.builder().page(PAGE, FakePage.builder().element(ElementSelector.css("#results"))
                        .build()).build())
                .build()) {
            assertThat(runtime.getInfo().isHeadless()).isFalse();
            BrowserSession session = runtime.openSession(BrowserSessionOptions.builder()
                    .obstaclePolicy(ObstaclePolicy.waitForHuman(Duration.ofMillis(50))).build());
            session.navigate(NavigationRequest.to(PAGE));
            session.resolveObstacle(PageObstacle.CONSENT, WaitCondition.present(ElementSelector.css("#results")));
            assertThatThrownBy(() -> session.resolveObstacle(PageObstacle.CHALLENGE,
                    WaitCondition.present(ElementSelector.css("#never"))))
                    .isInstanceOfSatisfying(PageObstacleException.class, e -> {
                        assertThat(e.isWaitedForHuman()).isTrue();
                        assertThat(e.getObstacle()).isEqualTo(PageObstacle.CHALLENGE);
                    });
        }
    }
}
