package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.model.analysis.AnalysisProvenance;
import com.aresstack.serp4j.core.model.analysis.SearchAnalysis;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.id.ElementId;
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

    @Test
    void equalResponsesAreEqualAndDifferentDataMetadataOrAnalysisAreNot() {
        SearchAnalysis analysis = SearchAnalysis.builder(AnalysisProvenance.of("test-analyzer")).build();
        SearchResponse response = SearchResponse.builder(SearchData.empty()).metadata(metadata).analysis(analysis)
                .build();
        SearchResponse same = SearchResponse.builder(SearchData.empty()).metadata(metadata).analysis(analysis).build();
        SearchData otherData = SearchData.builder()
                .result(SearchResultItem.builder(ElementId.of("a"), ResultType.ORGANIC, "https://e.org/a").build())
                .build();
        SearchMetadata otherMetadata = metadata.toBuilder().complete(false).build();
        SearchAnalysis otherAnalysis = SearchAnalysis.builder(AnalysisProvenance.of("other-analyzer")).build();

        assertThat(response).isEqualTo(same);
        assertThat(response.hashCode()).isEqualTo(same.hashCode());
        assertThat(response).isNotEqualTo(response.toBuilder().metadata(null).build());
        assertThat(response).isNotEqualTo(response.toBuilder().metadata(otherMetadata).build());
        assertThat(response).isNotEqualTo(response.toBuilder().analysis(otherAnalysis).build());
        assertThat(response).isNotEqualTo(response.withoutAnalysis());
        assertThat(response).isNotEqualTo(SearchResponse.builder(otherData).metadata(metadata).analysis(analysis)
                .build());
        assertThat(response).isNotEqualTo(null);
        assertThat(response).isNotEqualTo(SearchData.empty());
    }
}
