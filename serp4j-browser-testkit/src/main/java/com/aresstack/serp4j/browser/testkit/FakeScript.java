package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserValue;

/**
 * The result of a page script in the fake: the fake does not run JavaScript, a test registers what the script
 * returns. Throw {@link com.aresstack.serp4j.browser.ScriptEvaluationException} to simulate a failing script.
 */
public interface FakeScript {

    /**
     * @param context page, session and argument of the evaluation
     * @return the script result
     */
    BrowserValue evaluate(FakeScriptContext context);
}
