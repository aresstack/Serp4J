package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserRuntime;

/** The fake passes the same TCK as real runtimes. */
class FakeBrowserRuntimeContractTest extends BrowserRuntimeContractTest {

    private final FakeTckSite site = new FakeTckSite();

    @Override
    protected TckSite site() {
        return site;
    }

    @Override
    protected BrowserRuntime newRuntime() {
        return FakeBrowserRuntime.builder().web(site.getWeb()).maxConcurrentSessions(3).build();
    }

    @Override
    protected boolean crashBrowser(BrowserRuntime runtime, Object before) {
        ((FakeBrowserRuntime) runtime).simulateCrash();
        return true;
    }
}
