package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Value-type tests for the data package (Nachtrag A.1 #14): documented validation, equals/hashCode including
 * inequality, builders/factories and toString.
 */
class DataValueTypesTest {

    @Nested
    class MediaInfoTest {

        @Test
        void factoriesTrimUrlAndKeepOptionalDimensions() {
            MediaInfo plain = MediaInfo.of(" https://img.example/a.png ");
            MediaInfo sized = MediaInfo.of("https://img.example/a.png", 120, 80);

            assertThat(plain.getUrl()).isEqualTo("https://img.example/a.png");
            assertThat(plain.getWidth()).isEmpty();
            assertThat(plain.getHeight()).isEmpty();
            assertThat(sized.getWidth()).hasValue(120);
            assertThat(sized.getHeight()).hasValue(80);
        }

        @Test
        void rejectsBlankUrlAndNonPositiveDimensions() {
            assertThatIllegalArgumentException().isThrownBy(() -> MediaInfo.of(" ")).withMessageContaining("url");
            assertThatNullPointerException().isThrownBy(() -> MediaInfo.of(null));
            assertThatIllegalArgumentException().isThrownBy(() -> MediaInfo.of("https://e", 0, 10))
                    .withMessageContaining("width");
            assertThatIllegalArgumentException().isThrownBy(() -> MediaInfo.of("https://e", 10, -1))
                    .withMessageContaining("height");
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            MediaInfo info = MediaInfo.of("https://e/a.png", 10, 20);

            assertThat(info).isEqualTo(MediaInfo.of(" https://e/a.png ", 10, 20));
            assertThat(info.hashCode()).isEqualTo(MediaInfo.of("https://e/a.png", 10, 20).hashCode());
            assertThat(info).isNotEqualTo(MediaInfo.of("https://e/b.png", 10, 20));
            assertThat(info).isNotEqualTo(MediaInfo.of("https://e/a.png", 11, 20));
            assertThat(info).isNotEqualTo(MediaInfo.of("https://e/a.png", 10, 21));
            assertThat(info).isNotEqualTo(MediaInfo.of("https://e/a.png"));
            assertThat(info).isNotEqualTo(null);
            assertThat(info).isNotEqualTo("https://e/a.png");
        }

        @Test
        void toStringShowsUrlAndDimensionsWhenPresent() {
            assertThat(MediaInfo.of("https://e/a.png").toString()).isEqualTo("MediaInfo{url='https://e/a.png'}");
            assertThat(MediaInfo.of("https://e/a.png", 10, 20).toString())
                    .isEqualTo("MediaInfo{url='https://e/a.png', 10x20}");
        }
    }

    @Nested
    class PublishedDateTest {

        private final Instant instant = Instant.parse("2025-03-03T10:15:30Z");

        @Test
        void factoriesKeepInstantAndDisplayTextSeparately() {
            assertThat(PublishedDate.of(instant).getInstant()).contains(instant);
            assertThat(PublishedDate.of(instant).getDisplayText()).isEmpty();
            assertThat(PublishedDate.ofText(" 2 days ago ").getDisplayText()).contains("2 days ago");
            assertThat(PublishedDate.ofText("2 days ago").getInstant()).isEmpty();
            PublishedDate both = PublishedDate.of(instant, "Mar 3, 2025");
            assertThat(both.getInstant()).contains(instant);
            assertThat(both.getDisplayText()).contains("Mar 3, 2025");
        }

        @Test
        void rejectsMissingInstantAndBlankText() {
            assertThatNullPointerException().isThrownBy(() -> PublishedDate.of(null));
            assertThatNullPointerException().isThrownBy(() -> PublishedDate.of(null, "text"));
            assertThatIllegalArgumentException().isThrownBy(() -> PublishedDate.ofText(" "))
                    .withMessageContaining("displayText");
            assertThatNullPointerException().isThrownBy(() -> PublishedDate.ofText(null));
            assertThatIllegalArgumentException().isThrownBy(() -> PublishedDate.of(instant, ""));
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            PublishedDate date = PublishedDate.of(instant, "Mar 3, 2025");

            assertThat(date).isEqualTo(PublishedDate.of(instant, " Mar 3, 2025 "));
            assertThat(date.hashCode()).isEqualTo(PublishedDate.of(instant, "Mar 3, 2025").hashCode());
            assertThat(date).isNotEqualTo(PublishedDate.of(instant.plusSeconds(1), "Mar 3, 2025"));
            assertThat(date).isNotEqualTo(PublishedDate.of(instant, "Mar 4, 2025"));
            assertThat(date).isNotEqualTo(PublishedDate.of(instant));
            assertThat(date).isNotEqualTo(PublishedDate.ofText("Mar 3, 2025"));
            assertThat(date).isNotEqualTo(null);
            assertThat(date).isNotEqualTo(instant);
        }

        @Test
        void toStringNamesBothParts() {
            assertThat(PublishedDate.ofText("yesterday").toString())
                    .isEqualTo("PublishedDate{instant=null, displayText=yesterday}");
        }
    }

    @Nested
    class RatingTest {

        @Test
        void factoriesKeepValueAndOptionalMaxAndVotes() {
            assertThat(Rating.of(4.5).getValue()).isEqualTo(4.5);
            assertThat(Rating.of(4.5).getMax()).isEmpty();
            assertThat(Rating.of(4.5).getVotes()).isEmpty();
            assertThat(Rating.of(4.5, 5).getMax()).hasValue(5);
            assertThat(Rating.of(4.5, 5, 12).getVotes()).hasValue(12);
            assertThat(Rating.of(0, 0, 0).getValue()).isZero();
        }

        @Test
        void rejectsMaxBelowValue() {
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(4.0, 3.0)).withMessageContaining("max");
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(4.0, 3.0, 1));
        }

        @Test
        void rejectsNegativeNonFiniteAndNegativeVotes() {
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(-0.1)).withMessageContaining("value");
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(Double.NaN));
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(Double.POSITIVE_INFINITY));
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(1, Double.NaN));
            assertThatIllegalArgumentException().isThrownBy(() -> Rating.of(1, 5, -1)).withMessageContaining("votes");
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            Rating rating = Rating.of(4.5, 5, 12);

            assertThat(rating).isEqualTo(Rating.of(4.5, 5, 12));
            assertThat(rating.hashCode()).isEqualTo(Rating.of(4.5, 5, 12).hashCode());
            assertThat(rating).isNotEqualTo(Rating.of(4.0, 5, 12));
            assertThat(rating).isNotEqualTo(Rating.of(4.5, 10, 12));
            assertThat(rating).isNotEqualTo(Rating.of(4.5, 5, 13));
            assertThat(rating).isNotEqualTo(Rating.of(4.5, 5));
            assertThat(rating).isNotEqualTo(Rating.of(4.5));
            assertThat(rating).isNotEqualTo(null);
            assertThat(rating).isNotEqualTo(4.5);
        }

        @Test
        void toStringShowsOptionalPartsOnlyWhenPresent() {
            assertThat(Rating.of(4.5).toString()).isEqualTo("Rating{4.5}");
            assertThat(Rating.of(4.5, 5).toString()).isEqualTo("Rating{4.5/5.0}");
            assertThat(Rating.of(4.5, 5, 12).toString()).isEqualTo("Rating{4.5/5.0, votes=12}");
        }
    }

    @Nested
    class ResultProvenanceTest {

        private final ProviderId provider = ProviderId.of("brave-de");

        @Test
        void builderRequiresProviderAndKeepsOptionalOrigin() {
            ResultProvenance minimal = ResultProvenance.builder(provider).build();
            SerpPosition position = SerpPosition.ofRankInGroup(3);
            ResultProvenance full = ResultProvenance.builder(provider).engine(SearchEngineId.BRAVE)
                    .originalId(ElementId.of("o-1")).originalPosition(position).build();

            assertThat(minimal.getProvider()).isEqualTo(provider);
            assertThat(minimal.getEngine()).isEmpty();
            assertThat(minimal.getOriginalId()).isEmpty();
            assertThat(minimal.getOriginalPosition()).isEmpty();
            assertThat(full.getEngine()).contains(SearchEngineId.BRAVE);
            assertThat(full.getOriginalId()).contains(ElementId.of("o-1"));
            assertThat(full.getOriginalPosition()).contains(position);
            assertThatNullPointerException().isThrownBy(() -> ResultProvenance.builder(null))
                    .withMessageContaining("provider");
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            ResultProvenance provenance = ResultProvenance.builder(provider).engine(SearchEngineId.BRAVE)
                    .originalId(ElementId.of("o-1")).originalPosition(SerpPosition.ofRankInGroup(3)).build();
            ResultProvenance same = ResultProvenance.builder(ProviderId.of("Brave-DE")).engine(SearchEngineId.BRAVE)
                    .originalId(ElementId.of("o-1")).originalPosition(SerpPosition.ofRankInGroup(3)).build();

            assertThat(provenance).isEqualTo(same);
            assertThat(provenance.hashCode()).isEqualTo(same.hashCode());
            assertThat(provenance).isNotEqualTo(ResultProvenance.builder(ProviderId.of("other"))
                    .engine(SearchEngineId.BRAVE).originalId(ElementId.of("o-1"))
                    .originalPosition(SerpPosition.ofRankInGroup(3)).build());
            assertThat(provenance).isNotEqualTo(ResultProvenance.builder(provider).engine(SearchEngineId.GOOGLE)
                    .originalId(ElementId.of("o-1")).originalPosition(SerpPosition.ofRankInGroup(3)).build());
            assertThat(provenance).isNotEqualTo(ResultProvenance.builder(provider).engine(SearchEngineId.BRAVE)
                    .originalId(ElementId.of("o-2")).originalPosition(SerpPosition.ofRankInGroup(3)).build());
            assertThat(provenance).isNotEqualTo(ResultProvenance.builder(provider).engine(SearchEngineId.BRAVE)
                    .originalId(ElementId.of("o-1")).originalPosition(SerpPosition.ofRankInGroup(4)).build());
            assertThat(provenance).isNotEqualTo(ResultProvenance.builder(provider).build());
            assertThat(provenance).isNotEqualTo(null);
            assertThat(provenance).isNotEqualTo(provider);
        }

        @Test
        void toStringNamesProviderAndOrigin() {
            assertThat(ResultProvenance.builder(provider).engine(SearchEngineId.BRAVE).build().toString())
                    .startsWith("ResultProvenance{provider=").contains("brave-de").contains("engine=");
        }
    }

    @Nested
    class SiteLinkTest {

        @Test
        void factoriesTrimTextsAndTreatBlankAsAbsent() {
            SiteLink link = SiteLink.of(" https://e.org/about ", " About ", " ");

            assertThat(link.getUrl()).isEqualTo("https://e.org/about");
            assertThat(link.getTitle()).contains("About");
            assertThat(link.getDescription()).isEmpty();
            assertThat(SiteLink.of("https://e.org").getTitle()).isEmpty();
            assertThat(SiteLink.of("https://e.org", "t").getDescription()).isEmpty();
            assertThat(SiteLink.of("https://e.org", "t", "d").getDescription()).contains("d");
        }

        @Test
        void rejectsBlankUrl() {
            assertThatIllegalArgumentException().isThrownBy(() -> SiteLink.of(" ")).withMessageContaining("url");
            assertThatNullPointerException().isThrownBy(() -> SiteLink.of(null, "t"));
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            SiteLink link = SiteLink.of("https://e.org", "t", "d");

            assertThat(link).isEqualTo(SiteLink.of(" https://e.org ", " t ", " d "));
            assertThat(link.hashCode()).isEqualTo(SiteLink.of("https://e.org", "t", "d").hashCode());
            assertThat(link).isNotEqualTo(SiteLink.of("https://e.org/x", "t", "d"));
            assertThat(link).isNotEqualTo(SiteLink.of("https://e.org", "u", "d"));
            assertThat(link).isNotEqualTo(SiteLink.of("https://e.org", "t", "e"));
            assertThat(link).isNotEqualTo(SiteLink.of("https://e.org", "t"));
            assertThat(link).isNotEqualTo(SiteLink.of("https://e.org"));
            assertThat(link).isNotEqualTo(null);
            assertThat(link).isNotEqualTo("https://e.org");
        }

        @Test
        void toStringShowsUrlAndTitle() {
            assertThat(SiteLink.of("https://e.org", "t", "d").toString())
                    .isEqualTo("SiteLink{url='https://e.org', title=t}");
        }
    }

    @Nested
    class TextRangeTest {

        @Test
        void rangeIsHalfOpenAndValidated() {
            TextRange range = TextRange.of(0, 3);

            assertThat(range.getStart()).isZero();
            assertThat(range.getEnd()).isEqualTo(3);
            assertThat(range.length()).isEqualTo(3);
            assertThatIllegalArgumentException().isThrownBy(() -> TextRange.of(-1, 3)).withMessageContaining("start");
            assertThatIllegalArgumentException().isThrownBy(() -> TextRange.of(3, 3)).withMessageContaining("end");
            assertThatIllegalArgumentException().isThrownBy(() -> TextRange.of(4, 3));
        }

        @Test
        void equalsAndHashCodeCoverBothBoundsIncludingInequality() {
            assertThat(TextRange.of(1, 4)).isEqualTo(TextRange.of(1, 4));
            assertThat(TextRange.of(1, 4).hashCode()).isEqualTo(TextRange.of(1, 4).hashCode());
            assertThat(TextRange.of(1, 4)).isNotEqualTo(TextRange.of(2, 4));
            assertThat(TextRange.of(1, 4)).isNotEqualTo(TextRange.of(1, 5));
            assertThat(TextRange.of(1, 4)).isNotEqualTo(null);
            assertThat(TextRange.of(1, 4)).isNotEqualTo("[1,4)");
            assertThat(TextRange.of(1, 4).toString()).isEqualTo("[1,4)");
        }
    }

    @Nested
    class SerpPositionTest {

        @Test
        void ranksAreOneBasedAndAreaDefaultsToMain() {
            SerpPosition position = SerpPosition.ofRankInGroup(1);

            assertThat(position.getRankInGroup()).hasValue(1);
            assertThat(position.getRankAbsolute()).isEmpty();
            assertThat(position.getPage()).isEmpty();
            assertThat(position.getArea()).isEqualTo(SerpArea.MAIN);
            assertThatIllegalArgumentException().isThrownBy(() -> SerpPosition.builder().rankAbsolute(0));
            assertThatIllegalArgumentException().isThrownBy(() -> SerpPosition.builder().page(0));
            assertThatNullPointerException().isThrownBy(() -> SerpPosition.builder().area(null));
        }

        @Test
        void toBuilderRoundTripsAndEqualsCoversEveryFieldIncludingInequality() {
            SerpPosition position = SerpPosition.builder().rankInGroup(2).rankAbsolute(5).page(1)
                    .area(SerpArea.TOP).build();

            assertThat(position.toBuilder().build()).isEqualTo(position);
            assertThat(position.toBuilder().build().hashCode()).isEqualTo(position.hashCode());
            assertThat(position).isNotEqualTo(position.toBuilder().rankInGroup(3).build());
            assertThat(position).isNotEqualTo(position.toBuilder().rankAbsolute(6).build());
            assertThat(position).isNotEqualTo(position.toBuilder().page(2).build());
            assertThat(position).isNotEqualTo(position.toBuilder().area(SerpArea.SIDEBAR).build());
            assertThat(position).isNotEqualTo(SerpPosition.ofRankInGroup(2));
            assertThat(position).isNotEqualTo(null);
            assertThat(position).isNotEqualTo(SerpArea.TOP);
            assertThat(position.toString()).startsWith("SerpPosition{rankInGroup=2").contains("area=TOP");
        }
    }
}
