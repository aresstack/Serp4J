package com.aresstack.serp4j.http.resolve;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Resolves Google redirect URLs of the form {@code https://www.google.com/goto?url=<token>} (also without
 * {@code www.}) by one {@code GET} without following redirects and reading the {@code Location} header of the
 * {@code 3xx} answer. The target page itself is never fetched.
 *
 * <p>Security: only {@code https} on exactly {@code google.com} or {@code www.google.com} (no user info, port 443
 * or none), path {@code /goto} and a non-empty {@code url} parameter are accepted; the check is structural via
 * {@link URI}. The request carries no credential, cookie or referrer – it is built from scratch and never derived
 * from a provider request. Only an absolute {@code http}/{@code https} {@code Location} with a host is accepted.
 * Exceptions and messages never contain the URL or the token.</p>
 *
 * <p>Ownership (ADR-017): the transport belongs to the caller and is never closed here.</p>
 */
public final class GoogleGotoUrlResolver implements UrlResolver {

    /** Upper bound for one resolution; shorter when the caller passes less. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpTransport transport;

    public GoogleGotoUrlResolver(HttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** Whether {@code url} is a Google {@code /goto} redirect URL with a token; pure, no I/O. */
    public static boolean isGoogleGotoUrl(String url) {
        if (url == null) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("google.com") && !host.equals("www.google.com")) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        if (!"/goto".equals(uri.getRawPath())) {
            return false;
        }
        return token(uri.getRawQuery()).isPresent();
    }

    private static Optional<String> token(String rawQuery) {
        if (rawQuery == null) {
            return Optional.empty();
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.startsWith("url=")) {
                String value = pair.substring(4).trim();
                return value.isEmpty() ? Optional.<String>empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * The absolute {@code http}/{@code https} target named by {@code location}, or empty when it is missing, empty,
     * relative, has no host or another scheme.
     */
    static Optional<String> validLocation(Optional<String> location) {
        if (!location.isPresent() || location.get().trim().isEmpty()) {
            return Optional.empty();
        }
        String value = location.get().trim();
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawUserInfo() != null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    @Override
    public boolean supports(String url) {
        return isGoogleGotoUrl(url);
    }

    @Override
    public CompletableFuture<String> resolve(String url, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!isGoogleGotoUrl(url)) {
            return failed(new UrlResolutionException(UrlResolutionException.UNSUPPORTED,
                    "not a Google /goto URL with token"));
        }
        if (timeout.isNegative() || timeout.isZero()) {
            return failed(new UrlResolutionException(UrlResolutionException.TIMEOUT, "no time left to resolve"));
        }
        Duration bounded = timeout.compareTo(DEFAULT_TIMEOUT) > 0 ? DEFAULT_TIMEOUT : timeout;
        // Built from scratch: no Authorization, no Cookie, no Referer – nothing of the DataForSEO request.
        HttpRequest request = HttpRequest.get(url.trim())
                .setHeader("Accept", "text/html,*/*;q=0.8")
                .followRedirects(false)
                .timeout(bounded)
                .build();
        final CompletableFuture<HttpResponse> call;
        try {
            call = transport.execute(request);
        } catch (RuntimeException e) {
            return failed(new UrlResolutionException(UrlResolutionException.NETWORK,
                    "transport rejected the request: " + e.getClass().getSimpleName()));
        }
        final CompletableFuture<String> result = new CompletableFuture<String>();
        result.whenComplete((r, e) -> {
            if (!call.isDone()) {
                call.cancel(true);
            }
        });
        call.whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(failure(error));
                return;
            }
            if (!isRedirect(response.getStatus())) {
                result.completeExceptionally(new UrlResolutionException(UrlResolutionException.NOT_A_REDIRECT,
                        "Google answered HTTP " + response.getStatus() + " instead of a redirect"));
                return;
            }
            Optional<String> target = validLocation(response.getHeaders().getFirst("Location"));
            if (target.isPresent()) {
                result.complete(target.get());
            } else {
                result.completeExceptionally(new UrlResolutionException(UrlResolutionException.INVALID_LOCATION,
                        "redirect without absolute http(s) Location"));
            }
        });
        return result;
    }

    private static UrlResolutionException failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof HttpTransportException
                && ((HttpTransportException) cause).getKind() == HttpTransportException.Kind.TIMEOUT) {
            return new UrlResolutionException(UrlResolutionException.TIMEOUT, "redirect request timed out");
        }
        return new UrlResolutionException(UrlResolutionException.NETWORK,
                "redirect request failed: " + cause.getClass().getSimpleName());
    }

    private static <T> CompletableFuture<T> failed(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<T>();
        f.completeExceptionally(t);
        return f;
    }

    @Override
    public String toString() {
        return "GoogleGotoUrlResolver";
    }
}
