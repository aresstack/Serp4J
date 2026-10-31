package com.aresstack.serp4j.browser.testkit;

/**
 * The pages the TCK navigates, reachable under two hosts: a primary host and a foreign host with the same content
 * (for redirects and URL policy checks). {@link TckHttpServer} serves them over local HTTP for real browsers,
 * {@link FakeTckSite} as a {@link FakeWeb}. The paths and their behaviour are listed in {@link TckPages}.
 */
public interface TckSite {

    /**
     * @param path absolute path such as {@code /next}
     * @return the URL of the path on the primary host
     */
    String url(String path);

    /**
     * @param path absolute path
     * @return the URL of the path on the foreign host
     */
    String foreignUrl(String path);

    /**
     * @return a host pattern for {@link com.aresstack.serp4j.browser.UrlPolicy#allowHosts(String...)} that allows the
     * primary host and not the foreign host
     */
    String primaryHostPattern();
}
