package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.model.analysis.AnalysisProvenance;
import com.aresstack.serp4j.core.model.analysis.SearchAnalysis;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SearchResponseTest {

    private final SearchMetadata metadata = SearchMetadata.builder(QueryInfo.of("q")).provider(ProviderId.of("fake"))
            .rawPayload(RawPayload.ofText("text/plain", "raw")).build();

    @Test
    void dataIsMandatoryMetadataAndAnalysisOptional() {
        SearchResponse response = SearchResponse.builder(SearchData.empty()).build();

        assertThat(response.getData()).isEqualTo(SearchData.empty());
        assertThat(response.getMetadata()).isEmpty();
        assertThat(response.getAnalysis()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> SearchResponse.builder(null));
    }

    @Test
    void withAnalysisAndWithoutAnalysisAreCopies() {
        SearchResponse response = SearchResponse.of(SearchData.empty(), metadata);
        SearchAnalysis analysis = SearchAnalysis.builder(AnalysisProvenance.of("test-analyzer")).build();

        SearchResponse analysed = response.withAnalysis(analysis);

        assertThat(analysed.getAnalysis()).contains(analysis);
        assertThat(response.getAnalysis()).isEmpty();
        assertThat(analysed.withoutAnalysis()).isEqualTo(response);
    }

    @Test
    void withoutRawPayloadStripsMetadataPayloadOnly() {
        SearchResponse response = SearchResponse.of(SearchData.empty(), metadata);

        SearchResponse stripped = response.withoutRawPayload();

        assertThat(stripped.getMetadata().get().getRawPayload()).isEmpty();
        assertThat(stripped.getMetadata().get().getProvider()).contains(ProviderId.of("fake"));
        assertThat(stripped.withoutRawPayload()).isSameAs(stripped);
        assertThat(SearchResponse.builder(SearchData.empty()).build().withoutRawPayload().getMetadata()).isEmpty();
    }
}
