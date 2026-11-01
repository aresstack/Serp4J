package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserValue;
import com.aresstack.serp4j.browser.ElementSelector;
import com.aresstack.serp4j.browser.ScriptEvaluationException;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** The {@link TckPages} as a {@link FakeWeb} under {@code https://tck.serp4j.test} and {@code https://foreign.serp4j.test}. */
public final class FakeTckSite implements TckSite {

    private static final String PRIMARY = "tck.serp4j.test";
    private static final String FOREIGN = "foreign.serp4j.test";

    private final FakeWeb web;

    /** Creates the site. */
    public FakeTckSite() {
        FakeWeb.Builder builder = FakeWeb.builder()
                .script(TckPages.SUM_SCRIPT_ID, new FakeScript() {
                    @Override
                    public BrowserValue evaluate(FakeScriptContext context) {
                        BrowserValue argument = context.getArgument();
                        return BrowserValue.of(argument.path("a").get().asDouble() + argument.path("b").get().asDouble());
                    }
                })
                .script(TckPages.THROWING_SCRIPT_ID, new FakeScript() {
                    @Override
                    public BrowserValue evaluate(FakeScriptContext context) {
                        throw new ScriptEvaluationException(TckPages.THROWING_SCRIPT_ID, "Error: tck failure", null);
                    }
                })
                .script(TckPages.LANGUAGE_SCRIPT_ID, new FakeScript() {
                    @Override
                    public BrowserValue evaluate(FakeScriptContext context) {
                        return BrowserValue.of(context.getLocale().isPresent()
                                ? context.getLocale().get().toLanguageTag() : "en-US");
                    }
                });
        for (String host : Arrays.asList(PRIMARY, FOREIGN)) {
            register(builder, "https://" + host);
        }
        this.web = builder.build();
    }

    private void register(FakeWeb.Builder builder, String base) {
        Map<String, BrowserValue> structure = new LinkedHashMap<String, BrowserValue>();
        structure.put("title", BrowserValue.of(TckPages.HOME_TITLE));
        structure.put("count", BrowserValue.of(3));
        structure.put("items", BrowserValue.list(Arrays.asList(BrowserValue.of("alpha"), BrowserValue.of("beta"),
                BrowserValue.of("gamma"))));
        structure.put("flag", BrowserValue.of(true));
        structure.put("nothing", BrowserValue.nullValue());
        final BrowserValue structureValue = BrowserValue.map(structure);
        builder.page(base + TckPages.HOME, FakePage.builder()
                .title(TckPages.HOME_TITLE)
                .html(TckPages.homeHtml())
                .element(ElementSelector.css("#marker"))
                .element(ElementSelector.css("#intro"))
                .element(ElementSelector.css("#items"))
                .element(ElementSelector.css("#items li"), 3, true)
                .element(ElementSelector.css("#hidden"), 1, false)
                .element(ElementSelector.css("#go"))
                .element(ElementSelector.role("link", "Next"))
                .element(ElementSelector.role("button", "Go"))
                .onClick(ElementSelector.css("#next"), TckPages.NEXT)
                .onEnter(ElementSelector.css("#q"), TckPages.SEARCH + "?q={value}")
                .script(TckPages.STRUCTURE_SCRIPT_ID, new FakeScript() {
                    @Override
                    public BrowserValue evaluate(FakeScriptContext context) {
                        return structureValue;
                    }
                })
                .build());
        builder.page(base + TckPages.NEXT, FakePage.builder().title(TckPages.NEXT_TITLE).html(TckPages.nextHtml())
                .element(ElementSelector.css("#next-marker")).build());
        builder.route(base + TckPages.SEARCH, new FakeResponder() {
            @Override
            public FakePage respond(FakeRequest request) {
                String query = request.getQueryParameter("q").orElse("");
                return FakePage.builder().title(TckPages.SEARCH_TITLE_PREFIX + query).html(TckPages.searchHtml(query))
                        .element(ElementSelector.css("#result")).build();
            }
        });
        builder.page(base + TckPages.NOT_FOUND, FakePage.builder().status(404).title(TckPages.NOT_FOUND_TITLE)
                .html(TckPages.notFoundHtml()).build());
        builder.page(base + TckPages.SLOW, FakePage.builder().title("TCK Slow").delay(TckPages.SLOW_DELAY).build());
        builder.page(base + TckPages.REDIRECT_LOCAL, FakePage.redirect(TckPages.NEXT));
        builder.page(base + TckPages.REDIRECT_FOREIGN, FakePage.redirect("https://" + FOREIGN + TckPages.NEXT));
        builder.page(base + TckPages.COOKIE_SET, FakePage.builder().title("TCK Cookie Set").setCookie("tck", "1").build());
        builder.route(base + TckPages.COOKIE_SHOW, new FakeResponder() {
            @Override
            public FakePage respond(FakeRequest request) {
                String cookie = "1".equals(request.getCookies().get("tck")) ? "tck=1" : "none";
                return FakePage.builder().title(TckPages.COOKIE_TITLE_PREFIX + cookie).html(TckPages.cookieHtml(cookie))
                        .build();
            }
        });
        builder.page(base + TckPages.LATE, FakePage.builder().title(TckPages.LATE_TITLE).html(TckPages.lateHtml())
                .element(ElementSelector.css("#late"))
                .exchange(FakeExchange.get(TckPages.LATE_API).delay(TckPages.LATE_DELAY)
                        .header("Set-Cookie", "secret=1")
                        .body(new String(TckPages.lateBody(), Charset.forName("UTF-8")), "application/json")
                        .build())
                .build());
    }

    /**
     * @return the site as fake web
     */
    public FakeWeb getWeb() {
        return web;
    }

    @Override
    public String url(String path) {
        return "https://" + PRIMARY + path;
    }

    @Override
    public String foreignUrl(String path) {
        return "https://" + FOREIGN + path;
    }

    @Override
    public String primaryHostPattern() {
        return PRIMARY;
    }
}
