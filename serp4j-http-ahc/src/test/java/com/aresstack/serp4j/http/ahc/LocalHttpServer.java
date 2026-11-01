package com.aresstack.serp4j.http.ahc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A JDK {@link HttpServer} on 127.0.0.1 with a random port that records every request it receives. */
final class LocalHttpServer implements AutoCloseable {

    /** What the server saw. */
    static final class Recorded {
        final String method;
        final String uri;
        final Map<String, List<String>> headers;
        final byte[] body;

        Recorded(String method, String uri, Map<String, List<String>> headers, byte[] body) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
        }

        String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        List<String> headerValues(String name) {
            List<String> values = headers.get(name);
            return values == null ? Collections.<String>emptyList() : values;
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** Handles one request; may throw to make the server drop the connection. */
    interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Recorded> requests = new CopyOnWriteArrayList<Recorded>();
    final CountDownLatch release = new CountDownLatch(1);

    LocalHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.start();
    }

    LocalHttpServer on(String path, Handler handler) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                record(exchange);
                try {
                    handler.handle(exchange);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(e);
                } finally {
                    exchange.close();
                }
            }
        });
        return this;
    }

    String url(String pathAndQuery) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + pathAndQuery;
    }

    int port() {
        return server.getAddress().getPort();
    }

    List<Recorded> requests() {
        return new ArrayList<Recorded>(requests);
    }

    Recorded lastRequest() {
        return requests.get(requests.size() - 1);
    }

    private void record(HttpExchange exchange) throws IOException {
        Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            headers.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers,
                readAll(exchange.getRequestBody())));
    }

    static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head || bytes.length == 0 ? -1 : bytes.length);
        if (!head && bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(status, -1);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
