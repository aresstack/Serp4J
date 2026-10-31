package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Core contract B (ADR-008): optional provider/engine/vertical, never-empty contributing providers. */
class SearchMetadataContractTest {

    private static final ProviderId BRAVE = ProviderId.of("brave");
    private static final ProviderId DATAFORSEO = ProviderId.of("dataforseo");
    private static final QueryInfo QUERY = QueryInfo.of("serp4j");

    @Nested
    @DisplayName("single-provider response")
    class SingleProvider {

        @Test
        void providerImpliesContributingProviders() {
            SearchMetadata metadata = SearchMetadata.builder(QUERY).provider(BRAVE).build();

            assertThat(metadata.getProvider()).contains(BRAVE);
            assertThat(metadata.getContributingProviders()).containsExactly(BRAVE);
            assertThat(metadata.isSingleProviderResponse()).isTrue();
            assertThat(metadata.isMergedResponse()).isFalse();
        }

        @Test
        void explicitContributingProviderMustBeTheProvider() {
            assertThat(SearchMetadata.builder(QUERY).provider(BRAVE).contributingProvider(BRAVE).build()
                    .getContributingProviders()).containsExactly(BRAVE);

            assertThatIllegalStateException().isThrownBy(() ->
                    SearchMetadata.builder(QUERY).provider(BRAVE).contributingProvider(DATAFORSEO).build());
            assertThatIllegalStateException().isThrownBy(() ->
                    SearchMetadata.builder(QUERY).provider(BRAVE)
                            .contributingProviders(Arrays.asList(BRAVE, DATAFORSEO)).build());
        }

        @Test
        void forProviderSetsEngineVerticalAndTarget() {
            SearchMetadata metadata = SearchMetadata
                    .forProvider(BRAVE, SearchEngineId.BRAVE, SearchVertical.WEB, QUERY).build();

            assertThat(metadata.getEngine()).contains(SearchEngineId.BRAVE);
            assertThat(metadata.getVertical()).contains(SearchVertical.WEB);
            assertThat(metadata.getTarget()).contains(SearchTarget.web(SearchEngineId.BRAVE));
            assertThat(metadata.isComplete()).isTrue();
            assertThat(metadata.getWarnings()).isEmpty();
        }

        @Test
        void engineAndVerticalAreOptionalInTheType() {
            SearchMetadata metadata = SearchMetadata.builder(QUERY).provider(BRAVE).build();
            assertThat(metadata.getEngine()).isEmpty();
            assertThat(metadata.getVertical()).isEmpty();
            assertThat(metadata.getTarget()).isEmpty();
        }
    }

    @Nested
    @DisplayName("merged response")
    class Merged {

        @Test
        void needsAtLeastOneContributingProvider() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> SearchMetadata.builder(QUERY).build())
                    .withMessageContaining("contributing provider");
            assertThatIllegalStateException()
                    .isThrownBy(() -> SearchMetadata.forMerged(Collections.<ProviderId>emptyList(), QUERY).build());
        }

        @Test
        void keepsContributingProvidersInOrderWithoutProvider() {
            SearchMetadata metadata = SearchMetadata.forMerged(Arrays.asList(DATAFORSEO, BRAVE), QUERY).build();

            assertThat(metadata.getProvider()).isEmpty();
            assertThat(metadata.getContributingProviders()).containsExactly(DATAFORSEO, BRAVE);
            assertThat(metadata.isMergedResponse()).isTrue();
        }

        @Test
        void rejectsDuplicateContributingProviders() {
            assertThatIllegalStateException().isThrownBy(() ->
                    SearchMetadata.forMerged(Arrays.asList(BRAVE, BRAVE), QUERY).build());
        }

        @Test
        void queryIsAlwaysMandatory() {
            assertThat(SearchMetadata.forMerged(Collections.singletonList(BRAVE), QUERY).build().getQuery())
                    .isEqualTo(QUERY);
        }
    }

    @Test
    void withoutRawPayloadDropsOnlyThePayload() {
        SearchMetadata metadata = SearchMetadata.builder(QUERY).provider(BRAVE)
                .rawPayload(RawPayload.ofText("application/json", "{\"web\":{}}"))
                .warning(SearchWarning.of(SearchWarning.PARTIAL_RESULTS, "page 2 failed"))
                .complete(false)
                .build();

        SearchMetadata stripped = metadata.withoutRawPayload();

        assertThat(metadata.getRawPayload()).isPresent();
        assertThat(stripped.getRawPayload()).isEmpty();
        assertThat(stripped.getWarnings()).hasSize(1);
        assertThat(stripped.isComplete()).isFalse();
        assertThat(stripped.getContributingProviders()).containsExactly(BRAVE);
        assertThat(stripped.withoutRawPayload()).isSameAs(stripped);
    }

    @Test
    void toBuilderRoundTripsAndKeepsValueEquality() {
        SearchMetadata metadata = SearchMetadata.forProvider(BRAVE, SearchEngineId.BRAVE, SearchVertical.NEWS, QUERY)
                .page(PageInfo.builder(0, 10).hasMore(true).build())
                .build();

        assertThat(metadata.toBuilder().build()).isEqualTo(metadata).hasSameHashCodeAs(metadata);
        assertThat(metadata.toString()).doesNotContain("RawPayload{");
    }
}
