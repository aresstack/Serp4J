package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SearchRequestTest {

    @Test
    void defaultsAreWebVerticalFirstPageNoRawPayload() {
        SearchRequest request = SearchRequest.builder("serp4j").build();

        assertThat(request.getQuery()).isEqualTo("serp4j");
        assertThat(request.getVertical()).isEqualTo(SearchVertical.WEB);
        assertThat(request.getEngine()).isEmpty();
        assertThat(request.getTarget()).isEmpty();
        assertThat(request.getPaging()).isEqualTo(Paging.DEFAULT);
        assertThat(request.getRawPayloadPolicy()).isEqualTo(RawPayloadPolicy.NONE);
        assertThat(request.getRequiredFeatures()).containsExactly(SearchFeature.QUERY_TEXT);
        assertThat(request.getUsedFeatures()).containsExactly(SearchFeature.QUERY_TEXT);
        assertThat(request.getLocale()).isEmpty();
    }

    @Test
    void rejectsBlankQuery() {
        assertThatIllegalArgumentException().isThrownBy(() -> SearchRequest.builder("  "));
        assertThatNullPointerException().isThrownBy(() -> SearchRequest.builder(null));
    }

    @Test
    void targetSetsEngineAndVertical() {
        SearchRequest request = SearchRequest.builder("q")
                .target(SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.NEWS)).build();

        assertThat(request.getEngine()).contains(SearchEngineId.GOOGLE);
        assertThat(request.getVertical()).isEqualTo(SearchVertical.NEWS);
        assertThat(request.getTarget()).contains(SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.NEWS));
    }

    @Test
    void derivesUsedFeaturesFromContent() {
        SearchRequest request = SearchRequest.builder("q")
                .locale(SearchLocale.builder().countryCode("de").languageCode("de")
                        .location(GeoLocation.named("Berlin")).build())
                .offset(10).limit(20)
                .safeSearch(SafeSearch.STRICT)
                .timeRange(TimeRange.pastWeek())
                .device(DeviceType.MOBILE)
                .rawPayloadPolicy(RawPayloadPolicy.ON_ERROR)
                .build();

        assertThat(request.getUsedFeatures()).containsExactlyInAnyOrder(
                SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.MAX_RESULTS,
                SearchFeature.COUNTRY, SearchFeature.LANGUAGE, SearchFeature.GEO_LOCATION, SearchFeature.FRESHNESS,
                SearchFeature.SAFE_SEARCH, SearchFeature.DEVICE, SearchFeature.RAW_RESPONSE);
        assertThat(request.getLocale().get().getCountryCode()).contains("DE");
    }

    @Test
    void requireRaisesRequirementLevel() {
        SearchRequest request = SearchRequest.builder("q").timeRange(TimeRange.pastDay())
                .require(SearchFeature.FRESHNESS).build();

        assertThat(request.getRequirementLevel(SearchFeature.FRESHNESS)).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(request.getRequirementLevel(SearchFeature.COUNTRY)).isEqualTo(RequirementLevel.OPTIONAL);
        assertThat(request.getRequirementLevel(SearchFeature.QUERY_TEXT)).isEqualTo(RequirementLevel.REQUIRED);
    }

    @Test
    void providerOptionsAreKeyedByProviderAndTyped() {
        ProviderId brave = ProviderId.of("brave");
        ProviderRequestOptions options = () -> brave;
        SearchRequest request = SearchRequest.builder("q").providerOptions(options).build();

        assertThat(request.hasProviderOptions(brave)).isTrue();
        assertThat(request.getProviderOptions(brave, ProviderRequestOptions.class)).contains(options);
        assertThat(request.getProviderOptions(ProviderId.of("other"), ProviderRequestOptions.class)).isEmpty();
        assertThat(request.getProviderOptions()).containsOnlyKeys(brave);
    }

    @Test
    void emptyLocaleIsTreatedAsAbsent() {
        SearchRequest request = SearchRequest.builder("q").locale(SearchLocale.builder().build()).build();
        assertThat(request.getLocale()).isEmpty();
    }

    @Test
    void toBuilderRoundTripsWithValueEquality() {
        SearchRequest request = SearchRequest.builder("q").engine(SearchEngineId.BING).limit(25)
                .require(SearchFeature.MAX_RESULTS).build();
        SearchRequest copy = request.toBuilder().build();

        assertThat(copy).isEqualTo(request).hasSameHashCodeAs(request);
        assertThat(request.toBuilder().limit(30).build()).isNotEqualTo(request);
    }

    @Test
    void pagingValidatesOffsetAndLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> Paging.of(-1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> Paging.of(0, 0));
        assertThat(Paging.of(0, 10).next()).isEqualTo(Paging.of(10, 10));
    }
}
