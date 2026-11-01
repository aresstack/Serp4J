package com.aresstack.serp4j.provider.argos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text the {@code web_search} tool of the AskAI/Argos browser sidecar returns
 * ({@code BrowserMcpSidecarMain.registerTools} in AskAI):
 *
 * <pre>
 * PROVIDER: html.duckduckgo.com
 * 1: Title — https://target.example/page
 *    Snippet text
 * CHALLENGE: ...
 * ATTEMPT: html.duckduckgo.com ORGANIC_RESULTS
 * </pre>
 *
 * <p>{@code "No results."} is an empty result. The title may itself contain {@code " — "}; the URL is always the part
 * after the last separator. Unknown lines are ignored, so additive format changes in Argos do not break the
 * provider.</p>
 */
public final class ArgosSearchOutputParser {

    private static final String SEPARATOR = " — ";
    private static final Pattern ITEM = Pattern.compile("^(\\d+): (.*)$");
    private static final String PROVIDER = "PROVIDER: ";
    private static final String ATTEMPT = "ATTEMPT: ";
    private static final String CHALLENGE = "CHALLENGE: ";
    private static final String SNIPPET_INDENT = "   ";

    private ArgosSearchOutputParser() {
    }

    public static ArgosSearchOutput parse(String text) {
        ArgosSearchOutput.Builder output = ArgosSearchOutput.builder().rawText(text);
        if (text == null) {
            return output.build();
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(PROVIDER)) {
                String host = line.substring(PROVIDER.length()).trim();
                if (!host.isEmpty()) {
                    output.providerHost(host);
                }
            } else if (line.startsWith(CHALLENGE)) {
                String challenge = line.substring(CHALLENGE.length()).trim();
                if (!challenge.isEmpty()) {
                    output.challenge(challenge);
                }
            } else if (line.startsWith(ATTEMPT)) {
                String[] parts = line.substring(ATTEMPT.length()).trim().split("\\s+");
                if (parts.length >= 2) {
                    output.attempt(new ArgosSearchOutput.Attempt(parts[0], parts[1]));
                }
            } else {
                Matcher matcher = ITEM.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String rest = matcher.group(2);
                int separator = rest.lastIndexOf(SEPARATOR);
                if (separator < 0) {
                    continue;
                }
                String url = rest.substring(separator + SEPARATOR.length()).trim();
                if (url.isEmpty()) {
                    continue;
                }
                String snippet = "";
                if (i + 1 < lines.length && lines[i + 1].startsWith(SNIPPET_INDENT)) {
                    snippet = lines[++i].trim();
                }
                output.item(new ArgosSearchOutput.Item(matcher.group(1), rest.substring(0, separator).trim(), url,
                        snippet));
            }
        }
        return output.build();
    }
}
