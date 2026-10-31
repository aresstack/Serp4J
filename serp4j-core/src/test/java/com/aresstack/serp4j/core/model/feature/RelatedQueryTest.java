package com.aresstack.serp4j.core.model.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Value-type tests for {@link RelatedQuery} (Nachtrag A.1 #14). */
class RelatedQueryTest {

    @Test
    void factoriesTrimTextAndTreatBlankUrlAsAbsent() {
        RelatedQuery plain = RelatedQuery.of(" serp api ");
        RelatedQuery linked = RelatedQuery.of("serp api", " https://e.org/?q=serp+api ");

        assertThat(plain.getText()).isEqualTo("serp api");
        assertThat(plain.getUrl()).isEmpty();
        assertThat(linked.getUrl()).contains("https://e.org/?q=serp+api");
        assertThat(RelatedQuery.of("serp api", "  ").getUrl()).isEmpty();
        assertThat(RelatedQuery.of("serp api", null).getUrl()).isEmpty();
    }

    @Test
    void rejectsBlankOrMissingText() {
        assertThatIllegalArgumentException().isThrownBy(() -> RelatedQuery.of(" ")).withMessageContaining("text");
        assertThatNullPointerException().isThrownBy(() -> RelatedQuery.of(null));
        assertThatNullPointerException().isThrownBy(() -> RelatedQuery.of(null, "https://e.org"));
    }

    @Test
    void equalsAndHashCodeCoverTextAndUrlIncludingInequality() {
        RelatedQuery query = RelatedQuery.of("serp", "https://e.org");

        assertThat(query).isEqualTo(RelatedQuery.of(" serp ", " https://e.org "));
        assertThat(query.hashCode()).isEqualTo(RelatedQuery.of("serp", "https://e.org").hashCode());
        assertThat(query).isNotEqualTo(RelatedQuery.of("serps", "https://e.org"));
        assertThat(query).isNotEqualTo(RelatedQuery.of("serp", "https://e.org/other"));
        assertThat(query).isNotEqualTo(RelatedQuery.of("serp"));
        assertThat(query).isNotEqualTo(null);
        assertThat(query).isNotEqualTo("serp");
    }

    @Test
    void toStringShowsTheText() {
        assertThat(RelatedQuery.of("serp", "https://e.org").toString()).isEqualTo("RelatedQuery{text='serp'}");
    }
}
