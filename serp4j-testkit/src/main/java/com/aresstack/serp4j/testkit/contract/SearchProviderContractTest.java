package com.aresstack.serp4j.testkit.contract;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider;
import com.aresstack.serp4j.testkit.SharedInfrastructure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertNoSecret;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSingleProviderResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link SearchProvider} must fulfil (WP2 §5, architecture.md §5.4, ADR-008, ADR-017). Each test gets
 * fresh infrastructure and two providers ({@code contract-a}, {@code contract-b}) that borrow it.
 *
 * <ul>
 * <li><strong>Response contract (ADR-008):</strong> a single response names the provider, lists exactly it as
 * contributing provider, names engine, vertical and query; never contains analysis; unsupported required aspects
 * fail with {@code UNSUPPORTED}; programming errors throw synchronously, everything else fails through the
 * future.</li>
 * <li><strong>Context contract:</strong> a cancelled context fails with {@code CANCELLED}, an expired deadline with
 * {@code TIMEOUT}.</li>
 * <li><strong>Ownership contract (ADR-017):</strong> closing a provider never closes the borrowed infrastructure,
 * leaves the other provider on it working, is idempotent, and later calls fail with {@code CANCELLED}.</li>
 * <li><strong>Secrets (architecture.md §9):</strong> if {@link #secret()} names the credential the provider was
 * given, it never appears in descriptor, provider, response or failure texts.</li>
 * <li><strong>Capability invariant (WP2 §6.2):</strong> conversational capabilities are declared if and only if the
 * provider implements {@link ConversationalSearchProvider}.</li>
 * </ul>
 *
 * @param <I> the infrastructure the providers borrow, typically
 *            {@link com.aresstack.serp4j.testkit.http.FakeHttpTransport}
 */
public abstract class SearchProviderContractTest<I extends SharedInfrastructure> {

    /** Id of the provider most tests run against. */
    protected static final ProviderId PROVIDER_A = ProviderId.of("contract-a");
    /** Id of the second provider on the same infrastructure. */
    protected static final ProviderId PROVIDER_B = ProviderId.of("contract-b");

    protected I infrastructure;
    protected SearchProvider provider;
    protected SearchProvider secondProvider;

    /** Fresh infrastructure for one test, prepared (e.g. stubbed) so that {@link #supportedRequest()} succeeds. */
    protected abstract I createInfrastructure();

    /** Creates a provider with the given id that <em>borrows</em> {@code infrastructure}. Called twice per test. */
    protected abstract SearchProvider createProvider(ProviderId id, I infrastructure);

    /** A request the provider supports and answers successfully on the prepared infrastructure. */
    protected abstract SearchRequest supportedRequest();

    /** The secret (e.g. API key) the provider was configured with, to check it never leaks; default none. */
    protected Optional<String> secret() {
        return Optional.empty();
    }

    /** Context of normal calls; default {@link SearchContext#defaults()}. */
    protected SearchContext context() {
        return SearchContext.defaults();
    }

    @BeforeEach
    protected final void setUpContract() {
        infrastructure = createInfrastructure();
        provider = createProvider(PROVIDER_A, infrastructure);
        secondProvider = createProvider(PROVIDER_B, infrastructure);
    }

    @AfterEach
    protected final void tearDownContract() {
        // Teardown order of ADR-017 rule 7: providers first, then the infrastructure, by its owner (the test).
        provider.close();
        secondProvider.close();
        infrastructure.close();
    }

    protected final SearchResponse search(SearchProvider target, SearchRequest request) {
        return assertCompletes(target.search(request, context()));
    }

    @Nested
    @DisplayName("response contract (ADR-008)")
    class ResponseContract {

        @Test
        @DisplayName("descriptor names the provider and needs no I/O")
        void descriptorNamesTheProviderAndNeedsNoIo() {
            int usesBefore = infrastructure.getUses();
            ProviderDescriptor descriptor = provider.getDescriptor();
            assertEquals(PROVIDER_A, descriptor.getId());
            assertFalse(descriptor.getCapabilities().getTargets().isEmpty(), "provider declares at least one target");
            assertEquals(usesBefore, infrastructure.getUses(), "getDescriptor() must not use the infrastructure");
        }

        @Test
        @DisplayName("a single response names provider, engine, vertical and query")
        void metadataNamesProviderEngineVerticalAndQuery() {
            SearchRequest request = supportedRequest();
            SearchResponse response = search(provider, request);

            SearchMetadata metadata = assertSingleProviderResponse(response, PROVIDER_A);
            assertTrue(metadata.isSingleProviderResponse());
            assertEquals(request.getQuery(), metadata.getQuery().getOriginalQuery(), "original query");
            assertTrue(provider.getDescriptor().getCapabilities().supports(metadata.getTarget().get()),
                    "the searched target " + metadata.getTarget().get() + " is one the provider declares");
            if (request.getTarget().isPresent()) {
                assertEquals(request.getTarget(), metadata.getTarget(), "explicitly requested target is searched");
            }
        }

        @Test
        @DisplayName("provider never produces analysis")
        void providerNeverProducesAnalysis() {
            assertFalse(search(provider, supportedRequest()).getAnalysis().isPresent());
        }

        @Test
        @DisplayName("unsupported required feature fails with UNSUPPORTED through the future")
        void unsupportedRequiredFeatureFailsWithUnsupported() {
            SearchCapabilities capabilities = provider.getDescriptor().getCapabilities();
            SearchFeature unsupported = null;
            for (SearchFeature feature : SearchFeature.values()) {
                if (!capabilities.supports(feature)) {
                    unsupported = feature;
                    break;
                }
            }
            assumeTrue(unsupported != null, "provider supports every feature");
            SearchRequest request = supportedRequest().toBuilder().require(unsupported).build();
            assertFailsWith(provider.search(request, context()), SearchErrorCategory.UNSUPPORTED);
        }

        @Test
        @DisplayName("unsupported target fails with UNSUPPORTED through the future")
        void unsupportedTargetFailsWithUnsupported() {
            SearchTarget unknown = SearchTarget.of(SearchEngineId.of("serp4j-contract-unknown-engine"),
                    supportedRequest().getVertical());
            SearchRequest request = supportedRequest().toBuilder().target(unknown).build();
            assertFailsWith(provider.search(request, context()), SearchErrorCategory.UNSUPPORTED);
        }

        @Test
        @DisplayName("null arguments throw NullPointerException synchronously")
        void nullArgumentsThrowSynchronously() {
            assertThrows(NullPointerException.class, () -> provider.search(null, context()));
            assertThrows(NullPointerException.class, () -> provider.search(supportedRequest(), null));
        }

        @Test
        @DisplayName("concurrent searches are allowed")
        void concurrentSearchesAreAllowed() {
            List<CompletableFuture<SearchResponse>> futures = new ArrayList<CompletableFuture<SearchResponse>>();
            for (int i = 0; i < 4; i++) {
                futures.add(provider.search(supportedRequest(), context()));
            }
            for (CompletableFuture<SearchResponse> future : futures) {
                assertSingleProviderResponse(assertCompletes(future), PROVIDER_A);
            }
        }
    }

    @Nested
    @DisplayName("context contract (deadline and cancellation)")
    class ContextContract {

        @Test
        @DisplayName("cancelled context fails with CANCELLED")
        void cancelledContextFailsWithCancelled() {
            CancellationSource cancellation = new CancellationSource();
            cancellation.cancel();
            SearchContext cancelled = context().toBuilder().cancellation(cancellation.getSignal()).build();
            assertFailsWith(provider.search(supportedRequest(), cancelled), SearchErrorCategory.CANCELLED);
        }

        @Test
        @DisplayName("expired deadline fails with TIMEOUT")
        void expiredDeadlineFailsWithTimeout() {
            SearchContext expired = context().withDeadline(Instant.now().minusSeconds(1));
            assertFailsWith(provider.search(supportedRequest(), expired), SearchErrorCategory.TIMEOUT);
        }
    }

    @Nested
    @DisplayName("ownership contract (ADR-017)")
    class OwnershipContract {

        @Test
        @DisplayName("closing the provider does not close the borrowed infrastructure")
        void closingProviderDoesNotCloseInjectedInfrastructure() {
            search(provider, supportedRequest());
            provider.close();

            assertFalse(infrastructure.isClosed(), "injected infrastructure belongs to the caller");
            assertEquals(0, infrastructure.getCloseCalls());
        }

        @Test
        @DisplayName("closing one provider leaves the other provider on the same infrastructure working")
        void closingOneProviderLeavesOtherProviderWorking() {
            provider.close();

            SearchResponse response = search(secondProvider, supportedRequest());
            assertSingleProviderResponse(response, PROVIDER_B);
            assertFalse(infrastructure.isClosed());
        }

        @Test
        @DisplayName("close() is idempotent")
        void closeIsIdempotent() {
            provider.close();
            provider.close();
            assertEquals(0, infrastructure.getCloseCalls());
        }

        @Test
        @DisplayName("concurrent close() calls neither fail nor close the infrastructure (ADR-017 rule 8)")
        void concurrentCloseCallsDoNotFail() throws Exception {
            ConcurrentClose.run(4, provider::close);
            assertFalse(infrastructure.isClosed());
            assertEquals(0, infrastructure.getCloseCalls());
            assertFailsWith(provider.search(supportedRequest(), context()), SearchErrorCategory.CANCELLED);
        }

        @Test
        @DisplayName("search after close fails with CANCELLED through the future")
        void searchAfterCloseFailsWithCancelled() {
            provider.close();
            assertFailsWith(provider.search(supportedRequest(), context()), SearchErrorCategory.CANCELLED);
        }
    }

    @Nested
    @DisplayName("secrets (architecture.md §9)")
    class SecretContract {

        @Test
        @DisplayName("the configured secret never appears in descriptor, provider, response or failures")
        void secretNeverLeaks() {
            assumeTrue(secret().isPresent(), "no secret configured for this provider");
            String value = secret().get();
            assertNoSecret("provider", provider, value);
            assertNoSecret("descriptor", provider.getDescriptor(), value);
            assertNoSecret("response", search(provider, supportedRequest()), value);
            provider.close();
            SearchProviderException failure = assertFailsWith(provider.search(supportedRequest(), context()),
                    SearchErrorCategory.CANCELLED);
            assertNoSecret("failure", failure, value);
        }
    }

    @Nested
    @DisplayName("capability invariant (WP2 §6.2)")
    class CapabilityInvariant {

        @Test
        @DisplayName("conversational capabilities are declared iff the provider is conversational")
        void conversationalCapabilitiesPresentIffProviderIsConversational() {
            boolean declares = provider.getDescriptor().getCapabilities().getConversational().isPresent();
            assertEquals(provider instanceof ConversationalSearchProvider, declares);
        }
    }
}
