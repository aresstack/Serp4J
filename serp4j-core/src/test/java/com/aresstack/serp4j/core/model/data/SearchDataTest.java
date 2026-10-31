package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.ResultGroupFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ElementId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDataTest {

    private static SearchResultItem item(String id, ResultType type) {
        return SearchResultItem.builder(ElementId.of(id), type, "https://example.org/" + id).title("t " + id).build();
    }

    @Test
    void emptyIsSingletonAndBuilderWithoutElementsReturnsIt() {
        assertThat(SearchData.builder().build()).isSameAs(SearchData.empty());
        assertThat(SearchData.empty().isEmpty()).isTrue();
        assertThat(SearchData.empty().size()).isZero();
    }

    @Test
    void filtersResultsByTypeAndKeepsPageOrder() {
        SearchData data = SearchData.builder()
                .result(item("a", ResultType.PAID))
                .result(item("b", ResultType.ORGANIC))
                .result(item("c", ResultType.NEWS))
                .result(item("d", ResultType.ORGANIC))
                .build();

        assertThat(data.getResults()).extracting(i -> i.getId().getValue()).containsExactly("a", "b", "c", "d");
        assertThat(data.getOrganicResults()).extracting(i -> i.getId().getValue()).containsExactly("b", "d");
        assertThat(data.getResults(ResultType.NEWS)).hasSize(1);
        assertThat(data.getResults(ResultType.of("video"))).isEmpty();
    }

    @Test
    void rejectsDuplicateElementIdsAcrossItemsAndFeatures() {
        SerpFeature feature = RelatedQueriesFeature.builder(ElementId.of("a")).query("x").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SearchData.builder().result(item("a", ResultType.ORGANIC)).feature(feature).build())
                .withMessageContaining("duplicate element id");
        assertThatIllegalArgumentException().isThrownBy(() ->
                SearchData.builder().result(item("a", ResultType.ORGANIC)).result(item("a", ResultType.PAID)).build());
    }

    @Test
    void findsElementsAndFeaturesByIdClassAndType() {
        ResultGroupFeature group = ResultGroupFeature.builder(ElementId.of("g"), SerpFeatureType.TOP_STORIES)
                .item(ElementId.of("n1")).build();
        SearchData data = SearchData.builder()
                .result(item("n1", ResultType.NEWS))
                .feature(group)
                .feature(RelatedQueriesFeature.builder(ElementId.of("r")).query("more").build())
                .build();

        assertThat(data.findElement(ElementId.of("n1"))).containsInstanceOf(SearchResultItem.class);
        assertThat(data.findElement(ElementId.of("g"))).contains(group);
        assertThat(data.findElement(ElementId.of("missing"))).isEmpty();
        assertThat(data.findResult(ElementId.of("g"))).isEmpty();
        assertThat(data.getFeatures(ResultGroupFeature.class)).containsExactly(group);
        assertThat(data.getFeatures(SerpFeatureType.RELATED_SEARCHES)).hasSize(1);
        assertThat(data.size()).isEqualTo(3);
    }

    @Test
    void collectionsAreUnmodifiableAndCopied() {
        SearchData data = SearchData.builder().result(item("a", ResultType.ORGANIC)).build();
        assertThatThrownBy(() -> data.getResults().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(data.toBuilder().result(item("b", ResultType.ORGANIC)).build().size()).isEqualTo(2);
        assertThat(data.size()).isEqualTo(1);
    }

    @Test
    void resultItemBuilderTrimsTextsAndCopiesLists() {
        SearchResultItem item = SearchResultItem.builder(ElementId.of("x"), ResultType.ORGANIC, " https://e.org ")
                .title("  ").snippet(" snippet ").additionalSnippet("").additionalSnippet("more")
                .highlight(TextRange.of(0, 3)).build();

        assertThat(item.getUrl()).isEqualTo("https://e.org");
        assertThat(item.getTitle()).isEmpty();
        assertThat(item.getSnippet()).contains("snippet");
        assertThat(item.getAdditionalSnippets()).containsExactly("more");
        assertThat(item.getHighlights()).containsExactly(TextRange.of(0, 3));
        assertThat(item.getProvenance()).isEmpty();
        assertThat(item.toBuilder().build()).isEqualTo(item);
        assertThatIllegalArgumentException().isThrownBy(() -> TextRange.of(3, 3));
    }

    @Test
    void openValueTypesNormaliseCodes() {
        assertThat(ResultType.of(" Organic ")).isEqualTo(ResultType.ORGANIC);
        assertThatIllegalArgumentException().isThrownBy(() -> ResultType.of("with space"));
        assertThat(SerpPosition.builder().rankInGroup(1).rankAbsolute(3).page(1).area(SerpArea.TOP).build()
                .getRankAbsolute()).hasValue(3);
        assertThatIllegalArgumentException().isThrownBy(() -> SerpPosition.builder().rankInGroup(0));
    }
}
