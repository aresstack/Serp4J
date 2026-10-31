package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.model.id.ElementId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SearchAnalysisTest {

    @Test
    void mergeConcatenatesProvenanceAndLetsThisWinOnIntentAndMetrics() {
        SearchAnalysis first = SearchAnalysis.builder(AnalysisProvenance.of("intent-classifier"))
                .intent(SearchIntent.of(IntentCategory.INFORMATIONAL, 0.9))
                .metrics(SearchMetrics.builder().put(MetricKey.RESULT_COUNT, 10).build())
                .build();
        SearchAnalysis second = SearchAnalysis.builder(AnalysisProvenance.builder("entity-tagger").version("2").build())
                .intent(SearchIntent.of(IntentCategory.NAVIGATIONAL, 0.4))
                .entity(EntityMention.builder("Einstein").type("person").element(ElementId.of("r1")).build())
                .topic(Topic.of("physics", 0.7))
                .metrics(SearchMetrics.builder().put(MetricKey.RESULT_COUNT, 99).put(MetricKey.PAID_COUNT, 2).build())
                .build();

        SearchAnalysis merged = first.merge(second);

        assertThat(merged.getProvenance()).extracting(AnalysisProvenance::getAnalyzerId)
                .containsExactly("intent-classifier", "entity-tagger");
        assertThat(merged.getIntent().get().getCategory()).isEqualTo(IntentCategory.INFORMATIONAL);
        assertThat(merged.getEntities()).hasSize(1);
        assertThat(merged.getTopics()).hasSize(1);
        assertThat(merged.getMetrics().get(MetricKey.RESULT_COUNT)).hasValue(10);
        assertThat(merged.getMetrics().get(MetricKey.PAID_COUNT)).hasValue(2);
        assertThat(merged.toBuilder().build()).isEqualTo(merged);
    }

    @Test
    void validatesRangesAndCodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> SearchIntent.of(IntentCategory.LOCAL, 1.5));
        assertThatIllegalArgumentException().isThrownBy(() -> Topic.of("t", -0.1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SearchMetrics.builder().put(MetricKey.RESULT_COUNT, Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> AnalysisProvenance.of("Has Space"));
        assertThat(MetricKey.of("Custom.Metric").getCode()).isEqualTo("custom.metric");
    }
}
