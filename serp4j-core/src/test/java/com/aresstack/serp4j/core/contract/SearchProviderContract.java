package com.aresstack.serp4j.core.contract;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider;
import com.aresstack.serp4j.core.testsupport.FakeSharedResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract every {@link SearchProvider} must fulfil; template for the {@code SearchProviderContractTest} of the
 * testkit (Paket E). Adapters extend it, create their provider on top of the shared fake infrastructure and
 * inherit the checks:
 * <ul>
 * <li><strong>Response contract (review point B, architecture.md §5.4):</strong> metadata names the provider,
 * lists exactly it as contributing provider, carries engine, vertical and query.</li>
 * <li><strong>Ownership contract (review point C, ADR-017):</strong> closing a provider never closes injected
 * infrastructure, leaves other providers on the same infrastructure working, is idempotent, and later calls fail
 * with {@code CANCELLED} through the future.</li>
 * <li><strong>Capability invariant (WP2 §6.2):</strong> conversational capabilities are present if and only if the
 * provider implements {@link ConversationalSearchProvider}.</li>
 * </ul>
 */
public abstract class SearchProviderContract {

    protected FakeSharedResource sharedInfrastructure;
    protected SearchProvider provider;
    protected SearchProvider secondProvider;

    /** Creates a provider with the given id that <em>borrows</em> {@code shared}. */
    protected abstract SearchProvider createProvider(ProviderId id, FakeSharedResource shared);

    /** A request the provider under test supports. */
    protected abstract SearchRequest supportedRequest();

    protected SearchContext context() {
        return SearchContext.defaults();
    }

    @BeforeEach
    void setUpProviders() {
        sharedInfrastructure = new FakeSharedResource("shared-infrastructure");
        provider = createProvider(ProviderId.of("contract-a"), sharedInfrastructure);
        secondProvider = createProvider(ProviderId.of("contract-b"), sharedInfrastructure);
    }

    @AfterEach
    void closeProviders() {
        provider.close();
        secondProvider.close();
    }

    protected final SearchResponse search(SearchProvider target, SearchRequest request) throws Exception {
        return target.search(request, context()).get(10, TimeUnit.SECONDS);
    }

    @Nested
    @DisplayName("response contract (B)")
    class ResponseContract {

        @Test
        void descriptorNamesTheProviderAndNeedsNoIo() {
            ProviderDescriptor descriptor = provider.getDescriptor();
            assertThat(descriptor.getId()).isEqualTo(ProviderId.of("contract-a"));
            assertThat(descriptor.getCapabilities().getTargets()).isNotEmpty();
            assertThat(sharedInfrastructure.getUses()).as("descriptor must not use infrastructure").isZero();
        }

        @Test
        void metadataNamesProviderEngineVerticalAndQuery() throws Exception {
            SearchRequest request = supportedRequest();
            SearchResponse response = search(provider, request);

            assertThat(response.getMetadata()).isPresent();
            SearchMetadata metadata = response.getMetadata().get();
            assertThat(metadata.getProvider()).contains(provider.getDescriptor().getId());
            assertThat(metadata.getContributingProviders()).containsExactly(provider.getDescriptor().getId());
            assertThat(metadata.isSingleProviderResponse()).isTrue();
            assertThat(metadata.getEngine()).as("engine actually searched").isPresent();
            assertThat(metadata.getVertical()).as("vertical actually searched").isPresent();
            assertThat(metadata.getQuery().getOriginalQuery()).isEqualTo(request.getQuery());
            assertThat(provider.getDescriptor().getCapabilities().supports(metadata.getTarget().get()))
                    .as("the searched target is one the provider declares").isTrue();
        }

        @Test
        void providerNeverProducesAnalysis() throws Exception {
            assertThat(search(provider, supportedRequest()).getAnalysis()).isEmpty();
        }

        @Test
        void unsupportedRequiredFeatureFailsWithUnsupported() {
            SearchRequest request = supportedRequest().toBuilder()
                    .require(com.aresstack.serp4j.core.model.SearchFeature.ASYNC_TASK)
                    .rawPayloadPolicy(com.aresstack.serp4j.core.model.ext.RawPayloadPolicy.ALWAYS)
                    .require(com.aresstack.serp4j.core.model.SearchFeature.RAW_RESPONSE)
                    .build();
            if (provider.getDescriptor().getCapabilities().check(request).isSupported()) {
                return; // provider supports everything; nothing to check
            }
            assertCategory(provider.search(request, context()), SearchErrorCategory.UNSUPPORTED);
        }
    }

    @Nested
    @DisplayName("ownership contract (C, ADR-017)")
    class OwnershipContract {

        @Test
        void closingProviderDoesNotCloseInjectedInfrastructure() throws Exception {
            search(provider, supportedRequest());
            provider.close();

            assertThat(sharedInfrastructure.isClosed()).as("injected infrastructure belongs to the caller").isFalse();
            assertThat(sharedInfrastructure.getCloseCalls()).isZero();
        }

        @Test
        void closingOneProviderLeavesOtherProviderOnSameInfrastructureWorking() throws Exception {
            provider.close();

            SearchResponse response = search(secondProvider, supportedRequest());
            assertThat(response.getData().getResults()).isNotEmpty();
            assertThat(response.getMetadata().get().getProvider()).contains(ProviderId.of("contract-b"));
            assertThat(sharedInfrastructure.isClosed()).isFalse();
        }

        @Test
        void closeIsIdempotent() {
            provider.close();
            provider.close();
            assertThat(sharedInfrastructure.getCloseCalls()).isZero();
        }

        /** ADR-017 rule 8: close() is thread-safe; two concurrent calls neither fail nor touch borrowed resources. */
        @Test
        void concurrentCloseCallsDoNotFail() throws Exception {
            final int callers = 4;
            final CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(callers);
            try {
                List<Future<?>> closes = new ArrayList<Future<?>>();
                for (int i = 0; i < callers; i++) {
                    closes.add(executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                start.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                            provider.close();
                        }
                    }));
                }
                start.countDown();
                for (Future<?> close : closes) {
                    close.get(10, TimeUnit.SECONDS); // propagates any exception thrown by a concurrent close()
                }
            } finally {
                executor.shutdownNow();
            }
            assertThat(sharedInfrastructure.isClosed()).isFalse();
            assertThat(sharedInfrastructure.getCloseCalls()).isZero();
            assertCategory(provider.search(supportedRequest(), context()), SearchErrorCategory.CANCELLED);
        }

        @Test
        void searchAfterCloseFailsWithCancelledThroughTheFuture() {
            provider.close();
            assertCategory(provider.search(supportedRequest(), context()), SearchErrorCategory.CANCELLED);
        }
    }

    @Nested
    @DisplayName("capability invariant (WP2 §6.2)")
    class CapabilityInvariant {

        @Test
        void conversationalCapabilitiesPresentIffProviderIsConversational() {
            boolean declares = provider.getDescriptor().getCapabilities().getConversational().isPresent();
            boolean implements_ = provider instanceof ConversationalSearchProvider;
            assertThat(declares).isEqualTo(implements_);
        }
    }

    protected static void assertCategory(CompletableFuture<SearchResponse> future, SearchErrorCategory expected) {
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> {
                    Optional<SearchProviderException> failure = SearchProviderException.unwrap(e);
                    assertThat(failure).as("future fails with SearchProviderException").isPresent();
                    assertThat(failure.get().getCategory()).isEqualTo(expected);
                });
    }
}
