package com.aresstack.serp4j.browser.testkit;

import java.nio.charset.Charset;
import java.time.Duration;

/**
 * Paths, titles and HTML of the TCK site. Both {@link TckHttpServer} and {@link FakeTckSite} serve exactly this
 * behaviour:
 *
 * <ul>
 *   <li>{@value #HOME}: title {@value #HOME_TITLE}, marker {@code #marker}, link {@code #next}, three
 *       {@code #items li}, hidden {@code #hidden}, search form with input {@code #q}</li>
 *   <li>{@value #NEXT}: title {@value #NEXT_TITLE}</li>
 *   <li>{@value #SEARCH}{@code ?q=...}: title {@value #SEARCH_TITLE_PREFIX} followed by the query</li>
 *   <li>{@value #NOT_FOUND}: status 404, title {@value #NOT_FOUND_TITLE}</li>
 *   <li>{@value #SLOW}: answers after {@link #SLOW_DELAY}</li>
 *   <li>{@value #REDIRECT_LOCAL}: 302 to {@value #NEXT} on the same host</li>
 *   <li>{@value #REDIRECT_FOREIGN}: 302 to {@value #NEXT} on the foreign host</li>
 *   <li>{@value #COOKIE_SET}: sets cookie {@code tck=1}; {@value #COOKIE_SHOW}: title {@value #COOKIE_TITLE_PREFIX}
 *       followed by {@code tck=1} or {@code none}</li>
 *   <li>{@value #LATE}: fetches {@value #LATE_API} {@link #LATE_DELAY} after load; the JSON response carries a
 *       {@code Set-Cookie} header that must never be exposed and a body of {@link #LATE_BODY_BYTES} bytes</li>
 * </ul>
 */
public final class TckPages {

    /** Home page. */
    public static final String HOME = "/";
    /** Title of {@link #HOME}. */
    public static final String HOME_TITLE = "TCK Home";
    /** Second page. */
    public static final String NEXT = "/next";
    /** Title of {@link #NEXT}. */
    public static final String NEXT_TITLE = "TCK Next";
    /** Search result page of the home form. */
    public static final String SEARCH = "/search";
    /** Title prefix of {@link #SEARCH}. */
    public static final String SEARCH_TITLE_PREFIX = "TCK Search: ";
    /** A 404 page. */
    public static final String NOT_FOUND = "/status/404";
    /** Title of {@link #NOT_FOUND}. */
    public static final String NOT_FOUND_TITLE = "TCK Not Found";
    /** A page that answers late. */
    public static final String SLOW = "/slow";
    /** Delay of {@link #SLOW}. */
    public static final Duration SLOW_DELAY = Duration.ofSeconds(5);
    /** Redirect on the same host. */
    public static final String REDIRECT_LOCAL = "/redirect/local";
    /** Redirect to the foreign host. */
    public static final String REDIRECT_FOREIGN = "/redirect/foreign";
    /** Sets a cookie. */
    public static final String COOKIE_SET = "/cookie/set";
    /** Shows the cookie in the title. */
    public static final String COOKIE_SHOW = "/cookie/show";
    /** Title prefix of {@link #COOKIE_SHOW}. */
    public static final String COOKIE_TITLE_PREFIX = "TCK Cookie: ";
    /** Page with a late background fetch. */
    public static final String LATE = "/late";
    /** Title of {@link #LATE}. */
    public static final String LATE_TITLE = "TCK Late";
    /** Endpoint fetched by {@link #LATE}. */
    public static final String LATE_API = "/api/late.json";
    /** Delay of the background fetch after load. */
    public static final Duration LATE_DELAY = Duration.ofMillis(300);
    /** Size of the {@link #LATE_API} body. */
    public static final int LATE_BODY_BYTES = 1024;
    /** Script id of the structure script the TCK evaluates on {@link #HOME}. */
    public static final String STRUCTURE_SCRIPT_ID = "tck/structure-v1";
    /** Structure script: title, item count, item texts, a flag and a null. */
    public static final String STRUCTURE_SCRIPT = "() => ({ title: document.title,"
            + " count: document.querySelectorAll('#items li').length,"
            + " items: Array.from(document.querySelectorAll('#items li')).map(e => e.textContent),"
            + " flag: true, nothing: null })";
    /** Script id of the sum script. */
    public static final String SUM_SCRIPT_ID = "tck/sum-v1";
    /** Sum script: returns {@code arg.a + arg.b}. */
    public static final String SUM_SCRIPT = "(arg) => arg.a + arg.b";
    /** Script id of the failing script. */
    public static final String THROWING_SCRIPT_ID = "tck/throws-v1";
    /** A script that throws. */
    public static final String THROWING_SCRIPT = "() => { throw new Error('tck failure'); }";
    /** Script id of the language script. */
    public static final String LANGUAGE_SCRIPT_ID = "tck/language-v1";
    /** Returns {@code navigator.language}. */
    public static final String LANGUAGE_SCRIPT = "() => navigator.language";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private TckPages() {
    }

    static String page(String title, String body) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + escape(title) + "</title></head><body>"
                + body + "</body></html>";
    }

    /**
     * @return HTML of {@link #HOME}
     */
    public static String homeHtml() {
        return page(HOME_TITLE, "<h1 id=\"marker\">serp4j-tck</h1>"
                + "<p id=\"intro\">The Serp4J browser TCK home page.</p>"
                + "<a id=\"next\" href=\"" + NEXT + "\">Next</a>"
                + "<ul id=\"items\"><li>alpha</li><li>beta</li><li>gamma</li></ul>"
                + "<div id=\"hidden\" style=\"display:none\">hidden</div>"
                + "<form id=\"search\" action=\"" + SEARCH + "\" method=\"get\">"
                + "<input id=\"q\" name=\"q\" type=\"text\" aria-label=\"Query\"><button id=\"go\" type=\"submit\">Go</button>"
                + "</form>");
    }

    /**
     * @return HTML of {@link #NEXT}
     */
    public static String nextHtml() {
        return page(NEXT_TITLE, "<h1 id=\"next-marker\">next</h1>");
    }

    /**
     * @param query the query
     * @return HTML of {@link #SEARCH}
     */
    public static String searchHtml(String query) {
        return page(SEARCH_TITLE_PREFIX + query, "<h1 id=\"result\">" + escape(query) + "</h1>");
    }

    /**
     * @return HTML of {@link #NOT_FOUND}
     */
    public static String notFoundHtml() {
        return page(NOT_FOUND_TITLE, "<h1>not found</h1>");
    }

    /**
     * @param cookie {@code tck=1} or {@code none}
     * @return HTML of {@link #COOKIE_SHOW}
     */
    public static String cookieHtml(String cookie) {
        return page(COOKIE_TITLE_PREFIX + cookie, "<p id=\"cookie\">" + escape(cookie) + "</p>");
    }

    /**
     * @return HTML of {@link #LATE}
     */
    public static String lateHtml() {
        return page(LATE_TITLE, "<p id=\"late\">late</p><script>setTimeout(function () { fetch('" + LATE_API
                + "').then(function (r) { return r.text(); }); }, " + LATE_DELAY.toMillis() + ");</script>");
    }

    /**
     * @return body of {@link #LATE_API}, exactly {@link #LATE_BODY_BYTES} bytes of JSON
     */
    public static byte[] lateBody() {
        String prefix = "{\"late\":true,\"padding\":\"";
        String suffix = "\"}";
        StringBuilder json = new StringBuilder(prefix);
        while (json.length() < LATE_BODY_BYTES - suffix.length()) {
            json.append('x');
        }
        return json.append(suffix).toString().getBytes(UTF8);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
