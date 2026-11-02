package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.json.JsonTrees;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test transport that answers every request with a scripted function; records requests. Synthetic only. */
final class ScriptedTransport implements HttpTransport {

    final List<HttpRequest> requests = new CopyOnWriteArrayList<HttpRequest>();
    private final Function<HttpRequest, HttpResponse> script;
    volatile boolean closed;

    ScriptedTransport(Function<HttpRequest, HttpResponse> script) {
        this.script = script;
    }

    @Override
    public CompletableFuture<HttpResponse> execute(HttpRequest request) {
        requests.add(request);
        CompletableFuture<HttpResponse> future = new CompletableFuture<HttpResponse>();
        try {
            future.complete(script.apply(request));
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void close() {
        closed = true;
    }

    static StructuredValue body(HttpRequest request) {
        return JsonTrees.read(new String(request.getBody(), StandardCharsets.UTF_8));
    }

    static String method(HttpRequest request) {
        return body(request).get("method").flatMap(StructuredValue::asText).orElse("");
    }

    static long id(HttpRequest request) {
        java.util.Optional<StructuredValue> id = body(request).get("id");
        return id.isPresent() && id.get().asLong().isPresent() ? id.get().asLong().getAsLong() : -1L;
    }

    static HttpResponse json(int status, String json) {
        return HttpResponse.builder(status).textBody(json, "application/json").build();
    }

    static String toolResult(long id, String text, boolean isError) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                + JsonTrees.write(StructuredValue.of(text)) + "}],\"isError\":" + isError + "}}";
    }

    static String initResult(long id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":\"2025-03-26\","
                + "\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"synthetic\",\"version\":\"0\"}}}";
    }
}
