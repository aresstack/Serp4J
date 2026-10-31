package com.aresstack.serp4j.browser.testkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves the {@link TckPages} over plain HTTP on the loopback interface, for TCK runs against real browsers. The
 * primary host is {@code localhost}, the foreign host {@code 127.0.0.1}; both reach the same server. No test leaves
 * the machine.
 */
public final class TckHttpServer implements TckSite, AutoCloseable {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    private TckHttpServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
        this.port = server.getAddress().getPort();
    }

    /**
     * Starts the server on a free loopback port.
     *
     * @return the running server
     * @throws IOException if the port cannot be bound
     */
    public static TckHttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 64);
        final AtomicInteger threads = new AtomicInteger();
        ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "serp4j-tck-http-" + threads.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
        server.setExecutor(executor);
        final TckHttpServer[] holder = new TckHttpServer[1];
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                holder[0].handle(exchange);
            }
        });
        holder[0] = new TckHttpServer(server, executor);
        server.start();
        return holder[0];
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (TckPages.HOME.equals(path)) {
                html(exchange, 200, TckPages.homeHtml());
            } else if (TckPages.NEXT.equals(path)) {
                html(exchange, 200, TckPages.nextHtml());
            } else if (TckPages.SEARCH.equals(path)) {
                html(exchange, 200, TckPages.searchHtml(queryParameter(exchange.getRequestURI().getRawQuery(), "q")));
            } else if (TckPages.NOT_FOUND.equals(path)) {
                html(exchange, 404, TckPages.notFoundHtml());
            } else if (TckPages.SLOW.equals(path)) {
                try {
                    Thread.sleep(TckPages.SLOW_DELAY.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                html(exchange, 200, TckPages.page("TCK Slow", "<p>slow</p>"));
            } else if (TckPages.REDIRECT_LOCAL.equals(path)) {
                redirect(exchange, url(TckPages.NEXT));
            } else if (TckPages.REDIRECT_FOREIGN.equals(path)) {
                redirect(exchange, foreignUrl(TckPages.NEXT));
            } else if (TckPages.COOKIE_SET.equals(path)) {
                exchange.getResponseHeaders().add("Set-Cookie", "tck=1; Path=/");
                html(exchange, 200, TckPages.page("TCK Cookie Set", "<p>set</p>"));
            } else if (TckPages.COOKIE_SHOW.equals(path)) {
                html(exchange, 200, TckPages.cookieHtml(hasTckCookie(exchange) ? "tck=1" : "none"));
            } else if (TckPages.LATE.equals(path)) {
                html(exchange, 200, TckPages.lateHtml());
            } else if (TckPages.LATE_API.equals(path)) {
                exchange.getResponseHeaders().add("Set-Cookie", "secret=1; Path=/");
                send(exchange, 200, "application/json", TckPages.lateBody());
            } else if ("/favicon.ico".equals(path)) {
                send(exchange, 204, null, null);
            } else {
                html(exchange, 404, TckPages.page("404", "<p>unknown</p>"));
            }
        } finally {
            exchange.close();
        }
    }

    private static boolean hasTckCookie(HttpExchange exchange) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return false;
        }
        for (String header : headers) {
            for (String cookie : header.split(";")) {
                if ("tck=1".equals(cookie.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals(key)) {
                try {
                    return URLDecoder.decode(equals < 0 ? "" : pair.substring(equals + 1), "UTF-8");
                } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                    return "";
                }
            }
        }
        return "";
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        send(exchange, 302, null, null);
    }

    private static void html(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", html.getBytes(UTF8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        if (body == null || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        OutputStream out = exchange.getResponseBody();
        out.write(body);
        out.flush();
    }

    /**
     * @return the bound port
     */
    public int getPort() {
        return port;
    }

    @Override
    public String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Override
    public String foreignUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Override
    public String primaryHostPattern() {
        return "localhost";
    }

    /** Stops the server immediately. Idempotent. */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
