package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.testkit.FakeSharedResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertPending;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSingleProviderResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockSearchProviderTest {

    private static final ProviderId ID = ProviderId.of("mock");
    private static final SearchRequest REQUEST = SearchRequest.builder("serp4j").build();

    @Test
    void defaultAnswerIsThreeSyntheticResultsWithCompleteMetadata() {
        MockSearchProvider provider = MockSearchProvider.create(ID);

        SearchResponse response = assertCompletes(provider.search(REQUEST, SearchContext.defaults()));

        assertThat(response.getData().getOrganicResults()).hasSize(3);
        assertThat(response.getData().getOrganicResults().get(0).getUrl())
                .isEqualTo("https://example.invalid/mock/1");
        SearchMetadata metadata = assertSingleProviderResponse(response, ID);
        assertThat(metadata.getExecution()).isPresent();
        assertThat(provider.getRequests()).containsExactly(REQUEST);
    }

    @Test
    void syntheticResultsFollowPaging() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.syntheticResults(20)).build();
        SearchRequest secondPage = SearchRequest.builder("serp4j").offset(10).limit(5).build();

        SearchResponse response = assertCompletes(provider.search(secondPage, SearchContext.defaults()));

        assertThat(response.getData().getOrganicResults()).hasSize(5);
        assertThat(response.getData().getOrganicResults().get(0).getUrl()).endsWith("/11");
    }

    @ParameterizedTest
    @EnumSource(SearchErrorCategory.class)
    void injectsEveryFailureCategoryThroughTheFuture(SearchErrorCategory category) {
        MockSearchProvider provider = MockSearchProvider.failing(ID, category);

        SearchProviderException failure = assertFailsWith(provider.search(REQUEST, SearchContext.defaults()),
                category);

        assertThat(failure.getProviderId()).isEqualTo(ID);
    }

    @Test
    void injectsAPreparedExceptionUnchanged() {
        SearchProviderException rateLimited = SearchProviderException
                .builder(ID, SearchErrorCategory.RATE_LIMITED, "slow down")
                .retryAfter(Duration.ofSeconds(30)).providerStatusCode(429).build();
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.failure(rateLimited)).build();

        SearchProviderException failure = assertFailsWith(provider.search(REQUEST, SearchContext.defaults()),
                SearchErrorCategory.RATE_LIMITED);

        assertThat(failure).isSameAs(rateLimited);
        assertThat(failure.getRetryAfter()).contains(Duration.ofSeconds(30));
    }

    @Test
    void latencyDelaysTheAnswer() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).latency(Duration.ofMillis(150)).build();
        long start = System.nanoTime();

        CompletableFuture<SearchResponse> future = provider.search(REQUEST, SearchContext.defaults());
        assertPending(future);
        assertCompletes(future);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(140));
    }

    @Test
    void cancellingReleasesTheScheduledLatencyTask() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).latency(Duration.ofHours(1)).build();
        int before = com.aresstack.serp4j.testkit.internal.TestkitScheduler.queuedTasks();
        CompletableFuture<SearchResponse> future = provider.search(REQUEST, SearchContext.defaults());
        assertThat(com.aresstack.serp4j.testkit.internal.TestkitScheduler.queuedTasks()).isGreaterThan(before);

        provider.close();

        assertFailsWith(future, SearchErrorCategory.CANCELLED);
        assertThat(com.aresstack.serp4j.testkit.internal.TestkitScheduler.queuedTasks()).isEqualTo(before);
    }

    @Test
    void hangEndsWithTimeoutAtTheDeadline() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.hang()).build();
        SearchContext context = SearchContext.builder().timeout(Instant.now(), Duration.ofMillis(100)).build();

        assertFailsWith(provider.search(REQUEST, context), SearchErrorCategory.TIMEOUT);
    }

    @Test
    void deadlineShorterThanLatencyEndsWithTimeout() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).latency(Duration.ofSeconds(5)).build();
        SearchContext context = SearchContext.builder().timeout(Instant.now(), Duration.ofMillis(50)).build();

        assertFailsWith(provider.search(REQUEST, context), SearchErrorCategory.TIMEOUT);
    }

    @Test
    void cancellationEndsARunningSearch() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.hang()).build();
        CancellationSource cancellation = new CancellationSource();
        CompletableFuture<SearchResponse> future = provider.search(REQUEST,
                SearchContext.builder().cancellation(cancellation.getSignal()).build());
        assertPending(future);

        cancellation.cancel();

        assertFailsWith(future, SearchErrorCategory.CANCELLED);
    }

    @Test
    void closeCancelsRunningSearches() {
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.hang()).build();
        CompletableFuture<SearchResponse> future = provider.search(REQUEST, SearchContext.defaults());
        assertThat(provider.getRunningCount()).isEqualTo(1);

        provider.close();

        assertFailsWith(future, SearchErrorCategory.CANCELLED);
        assertThat(provider.getRunningCount()).isZero();
        assertThat(provider.isClosed()).isTrue();
    }

    @Test
    void answerCanBeSwappedDuringTheTest() {
        MockSearchProvider provider = MockSearchProvider.create(ID);
        assertCompletes(provider.search(REQUEST, SearchContext.defaults()));

        provider.setAnswer(Outcome.failure(SearchErrorCategory.QUOTA_EXHAUSTED));

        assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.QUOTA_EXHAUSTED);
    }

    @Test
    void answerRuleSeesTheRequest() {
        MockSearchProvider provider = MockSearchProvider.builder(ID)
                .answer(request -> request.getQuery().contains("fail")
                        ? Outcome.failure(SearchErrorCategory.BLOCKED) : Outcome.empty())
                .build();

        assertThat(assertCompletes(provider.search(REQUEST, SearchContext.defaults())).getData().getResults())
                .isEmpty();
        assertFailsWith(provider.search(SearchRequest.builder("please fail").build(), SearchContext.defaults()),
                SearchErrorCategory.BLOCKED);
    }

    @Test
    void givenDataGetsMetadataOfTheProvider() {
        SearchData data = SyntheticResults.organic(ProviderId.of("other"), REQUEST, 1);
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.results(data)).build();

        SearchResponse response = assertCompletes(provider.search(REQUEST, SearchContext.defaults()));

        assertThat(response.getData()).isEqualTo(data);
        assertSingleProviderResponse(response, ID);
    }

    @Test
    void preparedResponseIsDeliveredUnchanged() {
        SearchResponse merged = SearchResponse.of(SearchData.empty(), SearchMetadata
                .forMerged(java.util.Arrays.asList(ProviderId.of("a"), ProviderId.of("b")), QueryInfo.of("q"))
                .build());
        MockSearchProvider provider = MockSearchProvider.builder(ID).answer(Outcome.response(merged)).build();

        assertThat(assertCompletes(provider.search(REQUEST, SearchContext.defaults()))).isSameAs(merged);
    }

    @Test
    void unsupportedOptionalFeatureBecomesWarning() {
        MockSearchProvider provider = MockSearchProvider.create(ID);
        SearchRequest request = SearchRequest.builder("serp4j").safeSearch(com.aresstack.serp4j.core.model.SafeSearch.STRICT)
                .build();
        assertThat(request.getRequirementLevel(SearchFeature.SAFE_SEARCH)).isEqualTo(RequirementLevel.OPTIONAL);

        SearchResponse response = assertCompletes(provider.search(request, SearchContext.defaults()));

        assertThat(response.getMetadata().get().getWarnings())
                .extracting(SearchWarning::getCode).containsExactly(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void closedInfrastructureFailsWithUnavailable() {
        FakeSharedResource infrastructure = new FakeSharedResource("transport");
        MockSearchProvider provider = MockSearchProvider.builder(ID).infrastructure(infrastructure).build();
        infrastructure.close();

        assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.UNAVAILABLE);
    }

    @Test
    void conversationalCapabilitiesAreRejected() {
        assertThatThrownBy(() -> MockSearchProvider.builder(ID).capabilities(
                com.aresstack.serp4j.core.port.capability.SearchCapabilities.builder()
                        .target(com.aresstack.serp4j.core.model.id.SearchTarget.web(
                                com.aresstack.serp4j.core.model.id.SearchEngineId.GOOGLE))
                        .conversational(com.aresstack.serp4j.core.port.capability.ConversationalCapabilities
                                .builder().build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
