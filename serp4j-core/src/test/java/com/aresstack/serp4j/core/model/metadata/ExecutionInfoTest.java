package com.aresstack.serp4j.core.model.metadata;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** A.1 #6/#10/#14: provider-reported fields of {@link ExecutionInfo} (WP1 §4.8), toBuilder, validation, equality. */
class ExecutionInfoTest {

    private static final Instant STARTED = Instant.parse("2026-10-31T10:15:30Z");
    private static final Instant PROVIDER_TS = Instant.parse("2026-10-31T10:15:31Z");

    private static ExecutionInfo full() {
        return ExecutionInfo.builder()
                .startedAt(STARTED)
                .duration(Duration.ofMillis(850))
                .providerReportedDuration(Duration.ofMillis(620))
                .providerRequestId("task-1")
                .sourceUrl("https://www.google.com/search?q=serp4j")
                .attempts(2)
                .providerTimestamp(PROVIDER_TS)
                .build();
    }

    @Test
    void allFieldsAreEmptyByDefault() {
        ExecutionInfo info = ExecutionInfo.builder().build();

        assertThat(info.getStartedAt()).isEmpty();
        assertThat(info.getDuration()).isEmpty();
        assertThat(info.getProviderReportedDuration()).isEmpty();
        assertThat(info.getProviderRequestId()).isEmpty();
        assertThat(info.getSourceUrl()).isEmpty();
        assertThat(info.getAttempts()).isEmpty();
        assertThat(info.getProviderTimestamp()).isEmpty();
    }

    @Test
    void providerReportedDurationIsExposed() {
        ExecutionInfo info = ExecutionInfo.builder().providerReportedDuration(Duration.ofMillis(620)).build();

        assertThat(info.getProviderReportedDuration()).contains(Duration.ofMillis(620));
        assertThat(info.getDuration()).isEmpty();
        assertThat(info.toString()).contains("providerReportedDuration=PT0.62S");
    }

    @Test
    void providerReportedDurationMustNotBeNegative() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ExecutionInfo.builder().providerReportedDuration(Duration.ofMillis(-1)))
                .withMessageContaining("providerReportedDuration");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ExecutionInfo.builder().duration(Duration.ofMillis(-1)))
                .withMessageContaining("duration");
        assertThat(ExecutionInfo.builder().providerReportedDuration(Duration.ZERO).build()
                .getProviderReportedDuration()).contains(Duration.ZERO);
    }

    /** architecture.md §9: no secret in a string representation; provider URL echoes may carry query credentials. */
    @Test
    void toStringOmitsQueryStringAndFragmentOfTheSourceUrl() {
        String secret = "s3cr3t-9b8a7c6d-unique";
        ExecutionInfo info = ExecutionInfo.builder()
                .sourceUrl("https://example.test/search?q=serp4j&api_key=" + secret + "#top").build();

        assertThat(info.getSourceUrl()).contains("https://example.test/search?q=serp4j&api_key=" + secret + "#top");
        assertThat(info.toString()).doesNotContain(secret).doesNotContain("#top")
                .contains("sourceUrl=https://example.test/search?...");
        assertThat(ExecutionInfo.builder().sourceUrl("https://example.test/serp").build().toString())
                .contains("sourceUrl=https://example.test/serp");
    }

    @Test
    void sourceUrlIsTrimmedAndBlankMeansAbsent() {
        assertThat(ExecutionInfo.builder().sourceUrl("  https://example.test/serp ").build().getSourceUrl())
                .contains("https://example.test/serp");
        assertThat(ExecutionInfo.builder().sourceUrl("   ").build().getSourceUrl()).isEmpty();
        assertThat(ExecutionInfo.builder().sourceUrl(null).build().getSourceUrl()).isEmpty();
    }

    @Test
    void providerTimestampIsExposed() {
        ExecutionInfo info = ExecutionInfo.builder().providerTimestamp(PROVIDER_TS).build();

        assertThat(info.getProviderTimestamp()).contains(PROVIDER_TS);
        assertThat(info.getStartedAt()).isEmpty();
        assertThat(info.toString()).contains("providerTimestamp=" + PROVIDER_TS);
    }

    @Test
    void attemptsMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> ExecutionInfo.builder().attempts(0));
        assertThat(ExecutionInfo.builder().attempts(1).build().getAttempts()).hasValue(1);
    }

    @Test
    void toBuilderRoundTripKeepsEveryField() {
        ExecutionInfo original = full();

        ExecutionInfo copy = original.toBuilder().build();

        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
        assertThat(copy.getProviderReportedDuration()).contains(Duration.ofMillis(620));
        assertThat(copy.getSourceUrl()).contains("https://www.google.com/search?q=serp4j");
        assertThat(copy.getProviderTimestamp()).contains(PROVIDER_TS);
        assertThat(copy.getAttempts()).hasValue(2);
    }

    @Test
    void toBuilderChangeYieldsUnequalInstanceAndLeavesOriginalUntouched() {
        ExecutionInfo original = full();

        ExecutionInfo changed = original.toBuilder().attempts(3).build();

        assertThat(changed).isNotEqualTo(original);
        assertThat(original.getAttempts()).hasValue(2);
    }

    @Test
    void equalityCoversTheNewFields() {
        ExecutionInfo base = full();

        assertThat(base).isEqualTo(full());
        assertThat(base.hashCode()).isEqualTo(full().hashCode());
        assertThat(base).isNotEqualTo(base.toBuilder().providerReportedDuration(Duration.ofMillis(1)).build());
        assertThat(base).isNotEqualTo(base.toBuilder().sourceUrl("https://other.test").build());
        assertThat(base).isNotEqualTo(base.toBuilder().providerTimestamp(STARTED).build());
        assertThat(base).isNotEqualTo(base.toBuilder().providerRequestId("task-2").build());
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("ExecutionInfo");
    }

    /** WP1 §4.8 lists no cache flag; a provider must not report one through ExecutionInfo. */
    @Test
    void hasNoCacheFlag() {
        assertThat(ExecutionInfo.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("cached"));
        assertThat(ExecutionInfo.Builder.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("cached"));
    }
}
