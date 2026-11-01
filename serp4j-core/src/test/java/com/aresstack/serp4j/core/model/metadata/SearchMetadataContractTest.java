package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.SearchLocale;
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

    /** Finding #1: effectively used locale and device (architecture.md §4, WP1 §4.8). */
    @Nested
    @DisplayName("effective locale and device")
    class LocaleAndDevice {

        private final SearchLocale germany = SearchLocale.of("DE", "de");

        @Test
        void localeAndDeviceAreEmptyByDefault() {
            SearchMetadata metadata = SearchMetadata.builder(QUERY).provider(BRAVE).build();

            assertThat(metadata.getLocale()).isEmpty();
            assertThat(metadata.getDevice()).isEmpty();
        }

        @Test
        void builderSetsLocaleAndDeviceAndToBuilderKeepsThem() {
            SearchMetadata metadata = SearchMetadata
                    .forProvider(BRAVE, SearchEngineId.BRAVE, SearchVertical.WEB, QUERY)
                    .locale(germany)
                    .device(DeviceType.MOBILE)
                    .build();

            assertThat(metadata.getLocale()).contains(germany);
            assertThat(metadata.getDevice()).contains(DeviceType.MOBILE);
            SearchMetadata copy = metadata.toBuilder().build();
            assertThat(copy).isEqualTo(metadata).hasSameHashCodeAs(metadata);
            assertThat(copy.getLocale()).contains(germany);
            assertThat(copy.getDevice()).contains(DeviceType.MOBILE);
            SearchMetadata cleared = metadata.toBuilder().locale(null).device(null).build();
            assertThat(cleared.getLocale()).isEmpty();
            assertThat(cleared.getDevice()).isEmpty();
            assertThat(metadata.toString()).contains("locale=").contains("device=MOBILE");
        }

        @Test
        void differentLocaleOrDeviceBreaksEquality() {
            SearchMetadata.Builder base = SearchMetadata.builder(QUERY).provider(BRAVE)
                    .locale(germany).device(DeviceType.DESKTOP);
            SearchMetadata metadata = base.build();

            assertThat(base.locale(SearchLocale.of("US", "en")).build()).isNotEqualTo(metadata);
            assertThat(base.locale(germany).device(DeviceType.TABLET).build()).isNotEqualTo(metadata);
            assertThat(base.device(DeviceType.DESKTOP).build()).isEqualTo(metadata);
            assertThat(SearchMetadata.builder(QUERY).provider(BRAVE).build()).isNotEqualTo(metadata);
        }

        @Test
        void forMergedDerivesNothingAndLetsTheMergerSetAgreedValues() {
            SearchMetadata merged = SearchMetadata.forMerged(Arrays.asList(DATAFORSEO, BRAVE), QUERY).build();
            assertThat(merged.getLocale()).as("merger decides; nothing derived").isEmpty();
            assertThat(merged.getDevice()).isEmpty();

            SearchMetadata agreed = SearchMetadata.forMerged(Arrays.asList(DATAFORSEO, BRAVE), QUERY)
                    .locale(germany).device(DeviceType.DESKTOP).build();
            assertThat(agreed.getLocale()).contains(germany);
            assertThat(agreed.getDevice()).contains(DeviceType.DESKTOP);
            assertThat(agreed.isMergedResponse()).isTrue();
        }
    }

    /** Finding #13: equality must also distinguish, not only be reflexive. */
    @Nested
    @DisplayName("inequality")
    class Inequality {

        private SearchMetadata.Builder base() {
            return SearchMetadata.forProvider(BRAVE, SearchEngineId.BRAVE, SearchVertical.WEB, QUERY)
                    .rawPayload(RawPayload.ofText("application/json", "{\"a\":1}"))
                    .locale(SearchLocale.of("DE", "de"));
        }

        @Test
        void equalWhenBuiltTheSame() {
            assertThat(base().build()).isEqualTo(base().build()).hasSameHashCodeAs(base().build());
        }

        @Test
        void differentProviderIsNotEqual() {
            SearchMetadata other = SearchMetadata
                    .forProvider(DATAFORSEO, SearchEngineId.BRAVE, SearchVertical.WEB, QUERY)
                    .rawPayload(RawPayload.ofText("application/json", "{\"a\":1}"))
                    .locale(SearchLocale.of("DE", "de")).build();
            assertThat(other).isNotEqualTo(base().build());
        }

        @Test
        void incompleteIsNotEqualToComplete() {
            assertThat(base().complete(false).build()).isNotEqualTo(base().build());
        }

        @Test
        void differentRawPayloadIsNotEqual() {
            assertThat(base().rawPayload(RawPayload.ofText("application/json", "{\"a\":2}")).build())
                    .isNotEqualTo(base().build());
            assertThat(base().rawPayload(null).build()).isNotEqualTo(base().build());
        }

        @Test
        void differentLocaleIsNotEqual() {
            assertThat(base().locale(SearchLocale.of("US", "en")).build()).isNotEqualTo(base().build());
            assertThat(base().locale(null).build()).isNotEqualTo(base().build());
        }

        @Test
        void notEqualToNullOrOtherType() {
            assertThat(base().build()).isNotEqualTo(null).isNotEqualTo(QUERY);
        }
    }
}
