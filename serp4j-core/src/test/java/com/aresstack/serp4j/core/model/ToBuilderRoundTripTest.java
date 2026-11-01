package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.model.analysis.AnalysisProvenance;
import com.aresstack.serp4j.core.model.analysis.EntityMention;
import com.aresstack.serp4j.core.model.analysis.Topic;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.metadata.MonetaryAmount;
import com.aresstack.serp4j.core.model.metadata.ProviderUsage;
import com.aresstack.serp4j.core.model.metadata.RateLimitInfo;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.capability.ConversationalCapabilities;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A.1 #10: every public value class with a builder offers {@code toBuilder()} (architecture.md §3, ADR-004).
 * Each test round-trips an instance with all fields set and checks that a changed copy is not equal.
 */
class ToBuilderRoundTripTest {

    private static final ElementId E1 = ElementId.of("el-1");
    private static final ElementId E2 = ElementId.of("el-2");

    @Test
    void providerUsageToBuilderRoundTrips() {
        ProviderUsage original = ProviderUsage.builder()
                .cost(MonetaryAmount.of("0.002", "USD"))
                .credits(new java.math.BigDecimal("3"))
                .rateLimit(RateLimitInfo.builder().limit(100).build())
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().credits(new java.math.BigDecimal("4")).build()).isNotEqualTo(original);
    }

    @Test
    void rateLimitInfoToBuilderRoundTrips() {
        RateLimitInfo original = RateLimitInfo.builder()
                .limit(100)
                .remaining(42)
                .resetAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().remaining(41).build()).isNotEqualTo(original);
    }

    @Test
    void searchWarningToBuilderRoundTrips() {
        SearchWarning original = SearchWarning.builder(SearchWarning.PARTIAL_RESULTS, "only page one")
                .provider(ProviderId.of("brave"))
                .details(StructuredValue.objectBuilder().put("pages", 1).build())
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().provider(ProviderId.of("other")).build()).isNotEqualTo(original);
    }

    @Test
    void resultProvenanceToBuilderRoundTrips() {
        ResultProvenance original = ResultProvenance.builder(ProviderId.of("brave"), SearchEngineId.BRAVE)
                .originalId(E1)
                .originalPosition(SerpPosition.ofRankInGroup(3))
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().originalId(E2).build()).isNotEqualTo(original);
    }

    @Test
    void analysisProvenanceToBuilderRoundTrips() {
        AnalysisProvenance original = AnalysisProvenance.builder("keyword-analyzer")
                .version("1.2.3")
                .model("rules-v2")
                .analyzedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().version("1.2.4").build()).isNotEqualTo(original);
    }

    @Test
    void entityMentionToBuilderRoundTrips() {
        EntityMention original = EntityMention.builder("Ada Lovelace")
                .type("person")
                .confidence(0.9)
                .element(E1)
                .build();

        EntityMention copy = original.toBuilder().build();
        assertRoundTrip(original, copy);
        assertThat(copy.getElements()).containsExactly(E1);
        assertThat(original.toBuilder().element(E2).build()).isNotEqualTo(original);
        assertThat(original.getElements()).containsExactly(E1);
    }

    @Test
    void topicToBuilderRoundTrips() {
        Topic original = Topic.builder("history", 0.5).element(E1).build();

        Topic copy = original.toBuilder().build();
        assertRoundTrip(original, copy);
        assertThat(copy.getElements()).containsExactly(E1);
        assertThat(original.toBuilder().element(E2).build()).isNotEqualTo(original);
        assertThat(original.getElements()).containsExactly(E1);
    }

    @Test
    void factToBuilderRoundTrips() {
        Fact original = Fact.builder("Born")
                .value("1815")
                .value("London", "https://example.org/london")
                .build();

        Fact copy = original.toBuilder().build();
        assertRoundTrip(original, copy);
        assertThat(copy.getValues()).hasSize(2);
        assertThat(original.toBuilder().value("Paris").build()).isNotEqualTo(original);
        assertThat(original.getValues()).hasSize(2);
    }

    @Test
    void conversationalCapabilitiesToBuilderRoundTrips() {
        ConversationalCapabilities original = ConversationalCapabilities.builder()
                .maxTurns(10)
                .idleTimeout(Duration.ofMinutes(5))
                .maxSessionAge(Duration.ofHours(1))
                .maxConcurrentSessions(3)
                .resumable(true)
                .build();

        assertRoundTrip(original, original.toBuilder().build());
        assertThat(original.toBuilder().resumable(false).build()).isNotEqualTo(original);
        assertThat(original.toBuilder().maxConcurrentSessions(2).build()).isNotEqualTo(original);
    }

    private static void assertRoundTrip(Object original, Object copy) {
        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
        assertThat(copy.toString()).isEqualTo(original.toString());
    }
}
