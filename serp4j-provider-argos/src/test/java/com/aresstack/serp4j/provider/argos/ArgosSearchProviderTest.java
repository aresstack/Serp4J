package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.http.HttpTransportException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses only the synthetic fixture and synthetic outputs. */
class ArgosSearchProviderTest {

    private static final ProviderId ID = ProviderId.of("argos");

    /** Fake port answering with a fixed output or failure. */
    static final class FakeSearch implements ArgosWebSearch {
        final CompletableFuture<ArgosSearchOutput> answer = new CompletableFuture<ArgosSearchOutput>();
        final AtomicReference<Duration> lastTimeout = new AtomicReference<Duration>();
        String lastQuery;
        int closeCalls;

        @Override
        public CompletableFuture<ArgosSearchOutput> search(String query, Duration timeout) {
            lastQuery = query;
            lastTimeout.set(timeout);
            return answer;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static FakeSearch answering(String text) {
        FakeSearch search = new FakeSearch();
        search.answer.complete(ArgosSearchOutputParser.parse(text));
        return search;
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        try {
            future.join();
        } catch (RuntimeException e) {
            return McpArgosWebSearch.unwrap(e);
        }
        throw new AssertionError("expected failure");
    }

    @Test
    void mapsArgosHitsIntoTheDomainModel() {
        FakeSearch search = answering(ArgosSearchOutputParserTest.syntheticFixture());
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID, search).build();

        SearchResponse response = provider.search(SearchRequest.builder("synthetic query").build(),
                SearchContext.defaults()).join();

        assertThat(search.lastQuery).isEqualTo("synthetic query");
        assertThat(response.getData().getResults()).hasSize(3);
        SearchResultItem first = response.getData().getResults().get(0);
        assertThat(first.getUrl()).isEqualTo("https://example.invalid/argos/1");
        assertThat(first.getTitle()).contains("Synthetic result one");
        assertThat(first.getSnippet()).contains("First synthetic snippet.");
        assertThat(first.getPosition().get().getRankInGroup().getAsInt()).isEqualTo(1);
        assertThat(first.getProvenance().get(0).getEngine()).isEqualTo(SearchEngineId.DUCKDUCKGO);
        assertThat(first.getProvenance().get(0).getOriginalId().get().getValue()).isEqualTo("1");
        assertThat(response.getData().getResults().get(2).getSnippet()).isEmpty();
        assertThat(response.getMetadata().get().getProvider()).contains(ID);
        assertThat(response.getMetadata().get().getEngine()).contains(SearchEngineId.DUCKDUCKGO);
        assertThat(response.getMetadata().get().isComplete()).isTrue();
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .containsExactly(ArgosSearchProvider.WARNING_ENGINE_ATTEMPT);
        assertThat(response.getMetadata().get().getRawPayload()).isEmpty();
    }

    @Test
    void appliesTheResultLimitAndAttachesRawTextOnRequest() {
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID,
                answering(ArgosSearchOutputParserTest.syntheticFixture())).build();

        SearchResponse response = provider.search(SearchRequest.builder("q").limit(2)
                .rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build(), SearchContext.defaults()).join();

        assertThat(response.getData().getResults()).hasSize(2);
        assertThat(response.getMetadata().get().getRawPayload().get().getBodyAsText()).startsWith("# SYNTHETIC");
    }

    @Test
    void emptyResultWithFailedEnginesIsIncomplete() {
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID,
                answering("PROVIDER: www.bing.com\nATTEMPT: www.bing.com EXTRACTION_FAILED\n")).build();

        SearchResponse response = provider.search(SearchRequest.builder("q").build(), SearchContext.defaults())
                .join();

        assertThat(response.getData().getResults()).isEmpty();
        assertThat(response.getMetadata().get().isComplete()).isFalse();
        assertThat(response.getMetadata().get().getEngine()).contains(SearchEngineId.BING);
    }

    @Test
    void aChallengeWithoutHitsIsBlocked() {
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID,
                answering("CHALLENGE: bing.com captcha\nATTEMPT: www.bing.com CHALLENGE\n")).build();

        Throwable failure = failureOf(provider.search(SearchRequest.builder("q").build(), SearchContext.defaults()));

        assertThat(failure).isInstanceOf(SearchProviderException.class);
        assertThat(((SearchProviderException) failure).getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
    }

    @Test
    void requiredUnsupportedFeaturesFailAndOptionalOnesWarn() {
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID,
                answering(ArgosSearchOutputParserTest.syntheticFixture())).build();

        Throwable failure = failureOf(provider.search(SearchRequest.builder("q").offset(10)
                .require(SearchFeature.PAGINATION_OFFSET).build(), SearchContext.defaults()));
        SearchResponse optional = provider.search(SearchRequest.builder("q").offset(10).build(),
                SearchContext.defaults()).join();

        assertThat(((SearchProviderException) failure).getCategory()).isEqualTo(SearchErrorCategory.UNSUPPORTED);
        assertThat(optional.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void passesTheRemainingDeadlineAsTimeout() {
        FakeSearch search = answering("No results.");
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID, search).build();

        provider.search(SearchRequest.builder("q").build(),
                SearchContext.builder().timeout(Instant.now(), Duration.ofSeconds(30)).build()).join();

        assertThat(search.lastTimeout.get()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void translatesPortFailures() {
        assertThat(categoryFor(new HttpTransportException(HttpTransportException.Kind.CONNECT, "refused")))
                .isEqualTo(SearchErrorCategory.UNAVAILABLE);
        assertThat(categoryFor(new HttpTransportException(HttpTransportException.Kind.TIMEOUT, "slow")))
                .isEqualTo(SearchErrorCategory.TIMEOUT);
        assertThat(categoryFor(new ArgosWebSearchException(ArgosWebSearchException.Kind.HTTP_STATUS, "x", 404,
                null))).isEqualTo(SearchErrorCategory.CONFIGURATION);
        assertThat(categoryFor(new ArgosWebSearchException(ArgosWebSearchException.Kind.HTTP_STATUS, "x", 401,
                null))).isEqualTo(SearchErrorCategory.AUTHENTICATION);
        assertThat(categoryFor(new ArgosWebSearchException(ArgosWebSearchException.Kind.TOOL_ERROR, "x")))
                .isEqualTo(SearchErrorCategory.UNAVAILABLE);
        assertThat(categoryFor(new ArgosWebSearchException(ArgosWebSearchException.Kind.PROTOCOL, "x")))
                .isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }

    private static SearchErrorCategory categoryFor(Throwable portFailure) {
        FakeSearch search = new FakeSearch();
        search.answer.completeExceptionally(portFailure);
        Throwable failure = failureOf(ArgosSearchProvider.builder(ID, search).build()
                .search(SearchRequest.builder("q").build(), SearchContext.defaults()));
        return ((SearchProviderException) failure).getCategory();
    }

    @Test
    void cancellationCompletesTheSearch() {
        FakeSearch search = new FakeSearch(); // never answers
        CancellationSource cancellation = new CancellationSource();
        CompletableFuture<SearchResponse> running = ArgosSearchProvider.builder(ID, search).build()
                .search(SearchRequest.builder("q").build(),
                        SearchContext.builder().cancellation(cancellation.getSignal()).build());

        cancellation.cancel();

        assertThat(((SearchProviderException) failureOf(running)).getCategory())
                .isEqualTo(SearchErrorCategory.CANCELLED);
    }

    @Test
    void descriptorAndOwnership() {
        FakeSearch borrowed = new FakeSearch();
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID, borrowed).build();
        assertThat(provider.getDescriptor().getProviderType()).isEqualTo("argos");
        assertThat(provider.getDescriptor().getKind()).isEqualTo(ProviderKind.BROWSER);
        assertThat(provider.getDescriptor().getCapabilities().supports(SearchFeature.QUERY_TEXT)).isTrue();
        provider.close();
        assertThat(borrowed.closeCalls).isZero();
        assertThat(((SearchProviderException) failureOf(provider.search(SearchRequest.builder("q").build(),
                SearchContext.defaults()))).getCategory()).isEqualTo(SearchErrorCategory.CANCELLED);

        FakeSearch owned = new FakeSearch();
        ArgosSearchProvider owner = ArgosSearchProvider.builder(ID, owned).ownsSearch(true).build();
        owner.close();
        owner.close();
        assertThat(owned.closeCalls).isEqualTo(1);
    }

    @Test
    void mapsEngineHosts() {
        assertThat(ArgosSearchProvider.engineForHost("html.duckduckgo.com")).isEqualTo(SearchEngineId.DUCKDUCKGO);
        assertThat(ArgosSearchProvider.engineForHost("www.bing.com")).isEqualTo(SearchEngineId.BING);
        assertThat(ArgosSearchProvider.engineForHost("www.google.de")).isEqualTo(SearchEngineId.GOOGLE);
        assertThat(ArgosSearchProvider.engineForHost("search.example.invalid:8443").getCode())
                .isEqualTo("search.example.invalid");
    }

    @Test
    void rejectsNullArguments() {
        ArgosSearchProvider provider = ArgosSearchProvider.builder(ID, new FakeSearch()).build();
        assertThatThrownBy(() -> provider.search(null, SearchContext.defaults()))
                .isInstanceOf(NullPointerException.class);
    }
}
