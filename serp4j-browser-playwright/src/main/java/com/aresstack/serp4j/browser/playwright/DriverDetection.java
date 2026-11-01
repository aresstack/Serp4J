package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.playwright.spi.PlaywrightDriverModule;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Checks "exactly one driver" (ADR-011): exactly one registered {@link PlaywrightDriverModule} and exactly one
 * {@code com.microsoft.playwright.impl.driver.Driver} class on the class path, then the module's self-check.
 */
final class DriverDetection {

    static final String DRIVER_CLASS_RESOURCE = "com/microsoft/playwright/impl/driver/Driver.class";
    static final String HINT = "add exactly one of com.aresstack:serp4j-browser-playwright-driver-node (Java 8, "
            + "Node driver) or com.aresstack:serp4j-browser-playwright-driver-graal (Java 21, playwright4j) as runtime "
            + "dependency";

    /** Result: either a module or a non-ready readiness. */
    static final class Result {
        final PlaywrightDriverModule module;
        final BrowserReadiness failure;

        private Result(PlaywrightDriverModule module, BrowserReadiness failure) {
            this.module = module;
            this.failure = failure;
        }

        boolean isOk() {
            return module != null;
        }
    }

    private DriverDetection() {
    }

    static Result detect(ClassLoader classLoader) {
        List<PlaywrightDriverModule> modules = new ArrayList<PlaywrightDriverModule>();
        try {
            for (PlaywrightDriverModule module : ServiceLoader.load(PlaywrightDriverModule.class, classLoader)) {
                modules.add(module);
            }
        } catch (ServiceConfigurationError e) {
            return failure(BrowserReadiness.Status.INCOMPATIBLE_DRIVER, "driver module cannot be loaded: "
                    + e.getMessage());
        }
        int driverClasses = countResources(classLoader, DRIVER_CLASS_RESOURCE);
        return evaluate(modules, driverClasses, classLoader);
    }

    static Result evaluate(List<PlaywrightDriverModule> modules, int driverClasses, ClassLoader classLoader) {
        if (modules.isEmpty()) {
            return failure(BrowserReadiness.Status.DRIVER_NOT_FOUND, "no Playwright driver module found; " + HINT);
        }
        if (modules.size() > 1) {
            List<String> ids = new ArrayList<String>();
            for (PlaywrightDriverModule module : modules) {
                ids.add(module.getId());
            }
            return failure(BrowserReadiness.Status.MULTIPLE_DRIVERS, "more than one Playwright driver module found "
                    + ids + "; " + HINT);
        }
        PlaywrightDriverModule module = modules.get(0);
        if (driverClasses == 0) {
            return failure(BrowserReadiness.Status.DRIVER_NOT_FOUND, "driver module '" + module.getId()
                    + "' is present but no Playwright Driver class is on the class path; " + HINT);
        }
        if (driverClasses > 1) {
            return failure(BrowserReadiness.Status.MULTIPLE_DRIVERS, driverClasses
                    + " Playwright Driver classes on the class path (com.microsoft.playwright:driver together with "
                    + "playwright4j?); exclude com.microsoft.playwright:driver and :driver-bundle when using "
                    + "serp4j-browser-playwright-driver-graal");
        }
        String problem;
        try {
            problem = module.verify(classLoader);
        } catch (RuntimeException | LinkageError e) {
            problem = "self-check failed: " + e;
        }
        if (problem != null) {
            return failure(BrowserReadiness.Status.INCOMPATIBLE_DRIVER, "driver module '" + module.getId() + "': "
                    + problem);
        }
        return new Result(module, null);
    }

    private static Result failure(BrowserReadiness.Status status, String detail) {
        return new Result(null, BrowserReadiness.of(status, detail));
    }

    static int countResources(ClassLoader classLoader, String name) {
        try {
            Enumeration<URL> resources = classLoader.getResources(name);
            int count = 0;
            while (resources.hasMoreElements()) {
                resources.nextElement();
                count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
