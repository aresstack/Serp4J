/**
 * Serp4J provider for the browser web search of AskAI/Argos (Ports and Adapters).
 *
 * <ul>
 *   <li>{@link com.aresstack.serp4j.provider.argos.ArgosWebSearch} is the driven port: "run the Argos web search
 *       for this query". The provider knows nothing about MCP, HTTP or the sidecar process.</li>
 *   <li>{@link com.aresstack.serp4j.provider.argos.McpArgosWebSearch} is the adapter for that port. It calls the
 *       {@code web_search} tool of the AskAI/Argos browser sidecar ({@code browser-mcp-server-java21}) over MCP
 *       streamable HTTP via the Serp4J {@code HttpTransport} port.</li>
 *   <li>{@link com.aresstack.serp4j.provider.argos.ArgosSearchProvider} implements the core port
 *       {@code SearchProvider} and maps the Argos output into the Serp4J domain model.</li>
 *   <li>{@link com.aresstack.serp4j.provider.argos.ArgosSearchProviderFactory} registers the provider type
 *       {@code argos} via {@code java.util.ServiceLoader} (plugin).</li>
 * </ul>
 *
 * <p>There is no Gradle dependency on AskAI: AskAI lives in another repository, its sidecar requires Java 21 and
 * playwright4j. The only shared contract is the {@code web_search} tool and its text format, which
 * {@link com.aresstack.serp4j.provider.argos.ArgosSearchOutputParser} reads.</p>
 */
package com.aresstack.serp4j.provider.argos;
