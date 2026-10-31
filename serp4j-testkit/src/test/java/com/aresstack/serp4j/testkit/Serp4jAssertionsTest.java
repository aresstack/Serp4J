package com.aresstack.serp4j.testkit;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertNoSecret;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSessionFailure;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSingleProviderResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Serp4jAssertionsTest {

    private static final ProviderId ID = ProviderId.of("p");

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(failure);
        return future;
    }

    @Test
    void assertFailsWithChecksTheCategory() {
        CompletableFuture<Object> future = failed(new SearchProviderException(ID, SearchErrorCategory.TIMEOUT, "t"));

        assertThat(assertFailsWith(future, SearchErrorCategory.TIMEOUT).getProviderId()).isEqualTo(ID);
        assertThatThrownBy(() -> assertFailsWith(future, SearchErrorCategory.UNAVAILABLE))
                .isInstanceOf(AssertionError.class).hasMessageContaining("TIMEOUT");
        assertThatThrownBy(() -> assertFailsWith(CompletableFuture.completedFuture("ok"), SearchErrorCategory.TIMEOUT))
                .isInstanceOf(AssertionError.class).hasMessageContaining("completed with ok");
        assertThatThrownBy(() -> assertFailsWith(failed(new IllegalStateException("x")), SearchErrorCategory.TIMEOUT))
                .isInstanceOf(AssertionError.class).hasMessageContaining("IllegalStateException");
        assertThatThrownBy(() -> assertFailsWith(new CompletableFuture<Object>(), SearchErrorCategory.TIMEOUT,
                Duration.ofMillis(20))).isInstanceOf(AssertionError.class).hasMessageContaining("did not complete");
        assertThatThrownBy(() -> assertSessionFailure(future, SearchErrorCategory.TIMEOUT))
                .isInstanceOf(AssertionError.class).hasMessageContaining("SearchSessionException");
    }

    @Test
    void assertCompletesReportsFailures() {
        assertThat(assertCompletes(CompletableFuture.completedFuture("ok"))).isEqualTo("ok");
        assertThatThrownBy(() -> assertCompletes(failed(new IllegalStateException("boom"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("boom");
    }

    @Test
    void singleProviderResponseRejectsMergedOrForeignMetadata() {
        SearchResponse own = SearchResponse.of(SearchData.empty(), SearchMetadata.forProvider(ID,
                com.aresstack.serp4j.core.model.id.SearchEngineId.GOOGLE,
                com.aresstack.serp4j.core.model.id.SearchVertical.WEB, QueryInfo.of("q")).build());
        SearchResponse merged = SearchResponse.of(SearchData.empty(),
                SearchMetadata.forMerged(Arrays.asList(ID, ProviderId.of("q")), QueryInfo.of("q")).build());

        assertSingleProviderResponse(own, ID);
        assertThatThrownBy(() -> assertSingleProviderResponse(own, ProviderId.of("other")))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertSingleProviderResponse(merged, ID)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertSingleProviderResponse(SearchResponse.builder(SearchData.empty()).build(), ID))
                .isInstanceOf(AssertionError.class).hasMessageContaining("no metadata");
    }

    @Test
    void assertNoSecretFindsLeaksWithoutRepeatingThem() {
        Object leaky = new Object() {
            @Override
            public String toString() {
                return "Provider{key=very-secret-key}";
            }
        };
        assertNoSecret("safe", "Provider{key=****}", "very-secret-key");
        assertThatThrownBy(() -> assertNoSecret("leaky", leaky, "very-secret-key"))
                .isInstanceOf(AssertionError.class).hasMessageNotContaining("very-secret-key");
        SearchProviderException failure = SearchProviderException
                .builder(ID, SearchErrorCategory.AUTHENTICATION, "denied").detail("header", "very-secret-key").build();
        assertThatThrownBy(() -> assertNoSecret("failure", failure, "very-secret-key"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("details");
    }
}
