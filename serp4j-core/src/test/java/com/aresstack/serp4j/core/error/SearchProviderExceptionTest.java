package com.aresstack.serp4j.core.error;

import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SearchProviderExceptionTest {

    private static final ProviderId BRAVE = ProviderId.of("brave");

    @Test
    void retryableDefaultsToCategoryAndCanBeOverridden() {
        SearchProviderException rateLimited =
                new SearchProviderException(BRAVE, SearchErrorCategory.RATE_LIMITED, "429");
        SearchProviderException pinned = SearchProviderException.builder(BRAVE, SearchErrorCategory.RATE_LIMITED, "429")
                .retryable(false).retryAfter(Duration.ofSeconds(30)).providerStatusCode(429)
                .detail("endpoint", "/res/v1/web/search").build();

        assertThat(rateLimited.isRetryable()).isTrue();
        assertThat(pinned.isRetryable()).isFalse();
        assertThat(pinned.getRetryAfter()).contains(Duration.ofSeconds(30));
        assertThat(pinned.getProviderStatusCode()).hasValue(429);
        assertThat(pinned.getDetails()).containsEntry("endpoint", "/res/v1/web/search");
        assertThat(pinned.getProviderId()).isEqualTo(BRAVE);
        assertThat(SearchErrorCategory.RATE_LIMITED.isFallbackEligible()).isTrue();
        assertThat(SearchErrorCategory.INVALID_REQUEST.isFallbackEligible()).isFalse();
        assertThat(SearchErrorCategory.SESSION_LOST.isSessionCategory()).isTrue();
    }

    @Test
    void unwrapFindsTheExceptionBehindFutureWrappers() throws Exception {
        SearchProviderException cause = new SearchProviderException(BRAVE, SearchErrorCategory.TIMEOUT, "slow");
        CompletableFuture<String> future = new CompletableFuture<String>();
        future.completeExceptionally(cause);

        Throwable fromGet = null;
        try {
            future.get();
        } catch (ExecutionException e) {
            fromGet = e;
        }
        Throwable fromJoin = null;
        try {
            future.join();
        } catch (CompletionException e) {
            fromJoin = e;
        }

        assertThat(SearchProviderException.unwrap(fromGet)).contains(cause);
        assertThat(SearchProviderException.unwrap(fromJoin)).contains(cause);
        assertThat(SearchProviderException.unwrap(cause)).contains(cause);
        assertThat(SearchProviderException.unwrap(new IllegalStateException())).isEmpty();
        assertThat(SearchProviderException.unwrap(new CompletionException(new IllegalStateException()))).isEmpty();
        assertThat(SearchProviderException.unwrap(null)).isEmpty();
    }

    @Test
    void sessionExceptionCarriesSessionIdAndSameClassification() {
        SearchSessionId session = SearchSessionId.of("s-1");
        SearchSessionException exception = SearchSessionException
                .builder(session, BRAVE, SearchErrorCategory.SESSION_BUSY, "turn in flight")
                .detail("turn", "2").build();

        assertThat(exception.getSessionId()).isEqualTo(session);
        assertThat(exception.getCategory()).isEqualTo(SearchErrorCategory.SESSION_BUSY);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getDetails()).containsEntry("turn", "2");
        assertThat(exception).isInstanceOf(SearchProviderException.class);
        assertThat(SearchProviderException.unwrap(new ExecutionException(exception))).contains(exception);
    }
}
