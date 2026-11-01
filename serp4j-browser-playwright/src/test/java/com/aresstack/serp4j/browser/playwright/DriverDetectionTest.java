package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.playwright.spi.PlaywrightDriverModule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class DriverDetectionTest {

    private static final ClassLoader LOADER = DriverDetectionTest.class.getClassLoader();

    @Test
    void no_module_is_driver_not_found_with_a_hint() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Collections.<PlaywrightDriverModule>emptyList(), 1, LOADER);
        assertThat(result.isOk()).isFalse();
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.DRIVER_NOT_FOUND);
        assertThat(result.failure.getDetail()).contains("driver-node").contains("driver-graal");
    }

    @Test
    void two_modules_are_multiple_drivers() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Arrays.<PlaywrightDriverModule>asList(module("node", null), module("graal", null)), 1, LOADER);
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.MULTIPLE_DRIVERS);
        assertThat(result.failure.getDetail()).contains("node").contains("graal");
    }

    @Test
    void module_without_driver_class_is_driver_not_found() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Collections.singletonList(module("node", null)), 0, LOADER);
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.DRIVER_NOT_FOUND);
    }

    @Test
    void two_driver_classes_are_multiple_drivers() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Collections.singletonList(module("graal", null)), 2, LOADER);
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.MULTIPLE_DRIVERS);
        assertThat(result.failure.getDetail()).contains("exclude com.microsoft.playwright:driver");
    }

    @Test
    void failing_self_check_is_incompatible_driver() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Collections.singletonList(module("graal", "needs Java 21")), 1, LOADER);
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.INCOMPATIBLE_DRIVER);
        assertThat(result.failure.getDetail()).contains("needs Java 21");
    }

    @Test
    void throwing_self_check_is_incompatible_driver() {
        PlaywrightDriverModule throwing = new TestModule("x", null) {
            @Override
            public String verify(ClassLoader classLoader) {
                throw new NoClassDefFoundError("org/graalvm/polyglot/Context");
            }
        };
        DriverDetection.Result result = DriverDetection.evaluate(Collections.singletonList(throwing), 1, LOADER);
        assertThat(result.failure.getStatus()).isEqualTo(BrowserReadiness.Status.INCOMPATIBLE_DRIVER);
    }

    @Test
    void exactly_one_module_and_driver_class_is_ok() {
        DriverDetection.Result result = DriverDetection.evaluate(
                Collections.singletonList(module("node", null)), 1, LOADER);
        assertThat(result.isOk()).isTrue();
        assertThat(result.module.getId()).isEqualTo("node");
    }

    @Test
    void the_adapter_alone_has_no_driver() {
        assertThat(DriverDetection.detect(LOADER).failure.getStatus())
                .isEqualTo(BrowserReadiness.Status.DRIVER_NOT_FOUND);
    }

    private static PlaywrightDriverModule module(String id, String problem) {
        return new TestModule(id, problem);
    }

    private static class TestModule implements PlaywrightDriverModule {
        private final String id;
        private final String problem;

        TestModule(String id, String problem) {
            this.id = id;
            this.problem = problem;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDescription() {
            return id;
        }

        @Override
        public String verify(ClassLoader classLoader) {
            return problem;
        }
    }
}
