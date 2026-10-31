package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.TimeRange;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SearchCapabilitiesTest {

    private final SearchCapabilities capabilities = SearchCapabilities.builder()
            .target(SearchEngineId.BRAVE, SearchVertical.WEB)
            .target(SearchEngineId.BRAVE, SearchVertical.NEWS)
            .features(SearchFeature.COUNTRY, SearchFeature.LANGUAGE, SearchFeature.PAGINATION_OFFSET,
                    SearchFeature.MAX_RESULTS)
            .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.RELATED_QUERIES)
            .maxResultsPerRequest(20)
            .maxOffset(9)
            .build();

    @Test
    void queryTextIsAlwaysSupportedAndTargetsAreMandatory() {
        assertThat(capabilities.supports(SearchFeature.QUERY_TEXT)).isTrue();
        assertThat(capabilities.supports(SearchVertical.NEWS)).isTrue();
        assertThat(capabilities.supports(SearchVertical.IMAGES)).isFalse();
        assertThat(capabilities.delivers(ResponseFeature.RELATED_QUERIES)).isTrue();
        assertThat(capabilities.getConversational()).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> SearchCapabilities.builder().build());
    }

    @Test
    void supportedRequestPassesTheCheck() {
        SearchRequest request = SearchRequest.builder("q").engine(SearchEngineId.BRAVE)
                .locale(SearchLocale.of("de", "de")).offset(5).limit(20).build();

        assertThat(capabilities.check(request)).isEqualTo(CapabilityCheck.supported());
        assertThat(capabilities.supports(request)).isTrue();
    }

    @Test
    void unsupportedTargetIsAlwaysARequiredViolation() {
        SearchRequest byTarget = SearchRequest.builder("q").target(SearchTarget.web(SearchEngineId.GOOGLE)).build();
        SearchRequest byVertical = SearchRequest.builder("q").vertical(SearchVertical.IMAGES).build();

        CapabilityCheck targetCheck = capabilities.check(byTarget);
        assertThat(targetCheck.isSupported()).isFalse();
        assertThat(targetCheck.getRequiredViolations()).singleElement()
                .satisfies(v -> assertThat(v.getTarget()).contains(SearchTarget.web(SearchEngineId.GOOGLE)));

        CapabilityCheck verticalCheck = capabilities.check(byVertical);
        assertThat(verticalCheck.isSupported()).isFalse();
        assertThat(verticalCheck.getViolations()).singleElement()
                .satisfies(v -> assertThat(v.getVertical()).contains(SearchVertical.IMAGES));
    }

    @Test
    void optionalFeatureViolationsKeepTheRequestSupportedRequiredOnesDoNot() {
        SearchRequest optional = SearchRequest.builder("q").timeRange(TimeRange.pastWeek()).build();
        SearchRequest required = optional.toBuilder().require(SearchFeature.FRESHNESS).build();

        CapabilityCheck optionalCheck = capabilities.check(optional);
        assertThat(optionalCheck.isSupported()).isTrue();
        assertThat(optionalCheck.getOptionalViolations()).singleElement().satisfies(v -> {
            assertThat(v.getFeature()).contains(SearchFeature.FRESHNESS);
            assertThat(v.getLevel()).isEqualTo(RequirementLevel.OPTIONAL);
        });

        CapabilityCheck requiredCheck = capabilities.check(required);
        assertThat(requiredCheck.isSupported()).isFalse();
        assertThat(requiredCheck.getRequiredViolations()).hasSize(1);
    }

    @Test
    void pagingLimitsAreChecked() {
        SearchRequest tooMany = SearchRequest.builder("q").limit(21).build();
        SearchRequest tooFar = SearchRequest.builder("q").offset(10).require(SearchFeature.PAGINATION_OFFSET).build();

        assertThat(capabilities.check(tooMany).getViolations()).singleElement()
                .satisfies(v -> assertThat(v.getFeature()).contains(SearchFeature.MAX_RESULTS));
        assertThat(capabilities.check(tooFar).isSupported()).isFalse();
    }

    @Test
    void conversationalCapabilitiesAreValueObjects() {
        ConversationalCapabilities conversational = ConversationalCapabilities.builder()
                .maxTurns(10).idleTimeout(Duration.ofMinutes(5)).maxConcurrentSessions(2).resumable(true).build();
        SearchCapabilities withSessions = capabilities.toBuilder().conversational(conversational).build();

        assertThat(withSessions.getConversational()).contains(conversational);
        assertThat(conversational.getMaxTurns()).hasValue(10);
        assertThat(conversational.isResumable()).isTrue();
        assertThat(withSessions).isNotEqualTo(capabilities);
        assertThat(capabilities.toBuilder().build()).isEqualTo(capabilities);
    }

    @Test
    void requiredFeaturesAreCheckedEvenWithoutACorrespondingOption() {
        SearchRequest request = SearchRequest.builder("q").require(SearchFeature.FRESHNESS).build();
        assertThat(request.getUsedFeatures()).doesNotContain(SearchFeature.FRESHNESS);

        CapabilityCheck check = capabilities.check(request);
        assertThat(check.isSupported()).isFalse();
        assertThat(check.getRequiredViolations()).singleElement()
                .satisfies(v -> assertThat(v.getFeature()).contains(SearchFeature.FRESHNESS));

        SearchRequest supported = SearchRequest.builder("q").require(SearchFeature.COUNTRY).build();
        assertThat(capabilities.check(supported).isSupported()).isTrue();
    }
}
