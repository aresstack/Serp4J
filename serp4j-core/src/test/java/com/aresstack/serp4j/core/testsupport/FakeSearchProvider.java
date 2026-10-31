package com.aresstack.serp4j.core.testsupport;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal {@link SearchProvider} that follows the port contract: it borrows a {@link FakeSharedResource}, answers
 * every request with one organic hit, names itself in the metadata and never closes the borrowed resource.
 * Serves as reference implementation for the contract tests until the testkit module (Paket E) exists.
 */
public final class FakeSearchProvider implements SearchProvider {

    private final ProviderDescriptor descriptor;
    private final FakeSharedResource sharedResource;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger closeCalls = new AtomicInteger();

    public FakeSearchProvider(ProviderId id, FakeSharedResource sharedResource) {
        this.sharedResource = sharedResource;
        this.descriptor = ProviderDescriptor.builder(id, "fake", ProviderKind.MOCK, SearchCapabilities.builder()
                .target(SearchEngineId.GOOGLE, SearchVertical.WEB)
                .target(SearchEngineId.GOOGLE, SearchVertical.NEWS)
                .features(SearchFeature.PAGINATION_OFFSET, SearchFeature.MAX_RESULTS, SearchFeature.COUNTRY,
                        SearchFeature.LANGUAGE)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS)
                .maxResultsPerRequest(50)
                .build())
                .displayName("Fake provider " + id)
                .build();
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchResponse> search(SearchRequest request, SearchContext context) {
        if (request == null || context == null) {
            throw new NullPointerException("request and context must not be null");
        }
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        ProviderId id = descriptor.getId();
        if (closed.get()) {
            future.completeExceptionally(new SearchProviderException(id, SearchErrorCategory.CANCELLED,
                    "provider " + id + " is closed"));
            return future;
        }
        CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.isSupported()) {
            future.completeExceptionally(new SearchProviderException(id, SearchErrorCategory.UNSUPPORTED,
                    "unsupported: " + check.getRequiredViolations()));
            return future;
        }
        try {
            sharedResource.use();
        } catch (RuntimeException e) {
            future.completeExceptionally(new SearchProviderException(id, SearchErrorCategory.UNAVAILABLE,
                    "shared resource unavailable", e));
            return future;
        }
        SearchTarget target = request.getTarget().orElse(SearchTarget.of(SearchEngineId.GOOGLE, request.getVertical()));
        SearchData data = SearchData.builder()
                .result(SearchResultItem.builder(ElementId.of(id.getCode() + "-1"), ResultType.ORGANIC,
                        "https://example.org/" + id.getCode())
                        .title("Result of " + id)
                        .position(SerpPosition.ofRankInGroup(1))
                        .build())
                .build();
        SearchMetadata metadata = SearchMetadata
                .forProvider(id, target.getEngine(), target.getVertical(), QueryInfo.of(request.getQuery()))
                .build();
        future.complete(SearchResponse.of(data, metadata));
        return future;
    }

    @Override
    public void close() {
        closeCalls.incrementAndGet();
        closed.set(true);
        // Deliberately does NOT close sharedResource: it is borrowed (ADR-017).
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int getCloseCalls() {
        return closeCalls.get();
    }
}
