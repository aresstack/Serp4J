package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.WaitCondition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates CSS-only {@link WaitCondition}s in the page. The condition is passed as data to a fixed interpreter
 * function, never spliced into JavaScript source, so selectors and patterns cannot inject code. URL conditions are
 * evaluated in Java with the caller's {@link java.util.regex.Pattern} (Java regex semantics) against the current page
 * URL and passed in as a result.
 */
final class WaitScript {

    static final String PREDICATE = "(spec) => {\n"
            + "  const state = window.__serp4jQuiet || (window.__serp4jQuiet = {});\n"
            + "  const visible = (e) => { if (!e) return false; const r = e.getBoundingClientRect();"
            + " const s = window.getComputedStyle(e);"
            + " return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };\n"
            + "  const check = (c) => {\n"
            + "    switch (c.kind) {\n"
            + "      case 'present': return document.querySelector(c.css) !== null;\n"
            + "      case 'visible': return Array.from(document.querySelectorAll(c.css)).some(visible);\n"
            + "      case 'absent': return document.querySelector(c.css) === null;\n"
            + "      case 'count': return document.querySelectorAll(c.css).length >= c.count;\n"
            + "      case 'url': return c.matches === true;\n"
            + "      case 'quiet': {\n"
            + "        const key = c.css || ':document';\n"
            + "        const target = c.css ? document.querySelector(c.css) : document.documentElement;\n"
            + "        if (!target) return false;\n"
            + "        let q = state[key];\n"
            + "        if (!q || q.target !== target) {\n"
            + "          q = state[key] = { target: target, last: Date.now() };\n"
            + "          new MutationObserver(() => { q.last = Date.now(); }).observe(target,"
            + " { subtree: true, childList: true, attributes: true, characterData: true });\n"
            + "        }\n"
            + "        return Date.now() - q.last >= c.ms;\n"
            + "      }\n"
            + "      case 'all': return c.children.every(check);\n"
            + "      default: return false;\n"
            + "    }\n"
            + "  };\n"
            + "  return check(spec);\n"
            + "}";

    private WaitScript() {
    }

    /** Whether the condition tree contains a URL condition, which must be re-evaluated in Java on every poll. */
    static boolean hasUrlCondition(WaitCondition condition) {
        if (condition.getKind() == WaitCondition.Kind.URL_MATCHES) {
            return true;
        }
        for (WaitCondition child : condition.getChildren()) {
            if (hasUrlCondition(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a CSS-only condition into the argument of {@link #PREDICATE}.
     *
     * @param currentUrl the page URL that URL conditions are matched against
     */
    static Map<String, Object> spec(WaitCondition condition, String currentUrl) {
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        switch (condition.getKind()) {
            case SELECTOR_PRESENT:
                spec.put("kind", "present");
                break;
            case SELECTOR_VISIBLE:
                spec.put("kind", "visible");
                break;
            case SELECTOR_ABSENT:
                spec.put("kind", "absent");
                break;
            case SELECTOR_COUNT_AT_LEAST:
                spec.put("kind", "count");
                spec.put("count", condition.getCount());
                break;
            case URL_MATCHES:
                spec.put("kind", "url");
                spec.put("matches", condition.getUrlPattern().get().matcher(currentUrl).find());
                break;
            case DOM_QUIET:
                spec.put("kind", "quiet");
                spec.put("ms", (int) Math.min(Integer.MAX_VALUE, condition.getQuietPeriod().get().toMillis()));
                break;
            case ALL_OF:
                spec.put("kind", "all");
                List<Object> children = new ArrayList<Object>();
                for (WaitCondition child : condition.getChildren()) {
                    children.add(spec(child, currentUrl));
                }
                spec.put("children", children);
                break;
            default:
                throw new IllegalArgumentException("unsupported condition " + condition);
        }
        if (condition.getSelector().isPresent()) {
            if (!condition.getSelector().get().isCss()) {
                throw new IllegalArgumentException("only CSS selectors can be evaluated in the page: " + condition);
            }
            spec.put("css", condition.getSelector().get().getValue());
        }
        return spec;
    }
}
