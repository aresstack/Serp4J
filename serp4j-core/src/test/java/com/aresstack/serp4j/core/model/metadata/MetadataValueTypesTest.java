package com.aresstack.serp4j.core.model.metadata;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Value-type tests for the metadata package (Nachtrag A.1 #14): documented validation, equals/hashCode including
 * inequality, builders/factories and toString.
 */
class MetadataValueTypesTest {

    @Nested
    class MonetaryAmountTest {

        @Test
        void equalsByNumericValueAndCurrencyWithConsistentHashCode() {
            MonetaryAmount a = MonetaryAmount.of("1.0", "eur");
            MonetaryAmount b = MonetaryAmount.of("1.00", "EUR");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a.getCurrency()).isEqualTo("EUR");
            assertThat(a.getAmount()).isEqualByComparingTo(new BigDecimal("1"));
        }

        @Test
        void differentAmountOrCurrencyIsNotEqual() {
            MonetaryAmount one = MonetaryAmount.of("1.00", "USD");

            assertThat(one).isNotEqualTo(MonetaryAmount.of("1.01", "USD"));
            assertThat(one).isNotEqualTo(MonetaryAmount.of("1.00", "EUR"));
            assertThat(one).isNotEqualTo(null);
            assertThat(one).isNotEqualTo("1.00 USD");
        }

        @Test
        void rejectsNonIsoCurrencyCodesAndMissingValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> MonetaryAmount.of("1", "EURO"))
                    .withMessageContaining("ISO-4217");
            assertThatIllegalArgumentException().isThrownBy(() -> MonetaryAmount.of("1", "EU"));
            assertThatIllegalArgumentException().isThrownBy(() -> MonetaryAmount.of("1", "E1R"));
            assertThatIllegalArgumentException().isThrownBy(() -> MonetaryAmount.of("1", " "));
            assertThatIllegalArgumentException().isThrownBy(() -> MonetaryAmount.of(" ", "USD"));
            assertThatNullPointerException().isThrownBy(() -> MonetaryAmount.of((BigDecimal) null, "USD"));
            assertThatNullPointerException().isThrownBy(() -> MonetaryAmount.of("1", null));
        }

        @Test
        void addsSameCurrencyOnlyAndRejectsMixedCurrencies() {
            MonetaryAmount sum = MonetaryAmount.of("1.25", "USD").add(MonetaryAmount.of("0.75", "usd"));

            assertThat(sum).isEqualTo(MonetaryAmount.of("2", "USD"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> MonetaryAmount.of("1", "USD").add(MonetaryAmount.of("1", "EUR")))
                    .withMessageContaining("EUR").withMessageContaining("USD");
            assertThatNullPointerException().isThrownBy(() -> MonetaryAmount.of("1", "USD").add(null));
        }

        @Test
        void toStringIsPlainAmountAndCurrency() {
            assertThat(MonetaryAmount.of("1.50", "usd").toString()).isEqualTo("1.50 USD");
            assertThat(MonetaryAmount.of(new BigDecimal("1E+3"), "USD").toString()).isEqualTo("1000 USD");
        }
    }

    @Nested
    class ProviderUsageTest {

        @Test
        void emptyBuilderYieldsAllOptionalsEmpty() {
            ProviderUsage usage = ProviderUsage.builder().build();

            assertThat(usage.getCost()).isEmpty();
            assertThat(usage.getCreditsUsed()).isEmpty();
            assertThat(usage.getRateLimit()).isEmpty();
        }

        @Test
        void builderKeepsAllFields() {
            RateLimitInfo rateLimit = RateLimitInfo.builder().limit(10).remaining(3).build();
            ProviderUsage usage = ProviderUsage.builder()
                    .cost(MonetaryAmount.of("0.002", "USD")).creditsUsed(0).rateLimit(rateLimit).build();

            assertThat(usage.getCost()).contains(MonetaryAmount.of("0.0020", "USD"));
            assertThat(usage.getCreditsUsed()).hasValue(0);
            assertThat(usage.getRateLimit()).contains(rateLimit);
        }

        @Test
        void rejectsNegativeCredits() {
            assertThatIllegalArgumentException().isThrownBy(() -> ProviderUsage.builder().creditsUsed(-1))
                    .withMessageContaining("creditsUsed");
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            RateLimitInfo rateLimit = RateLimitInfo.builder().limit(10).build();
            ProviderUsage usage = ProviderUsage.builder()
                    .cost(MonetaryAmount.of("1", "USD")).creditsUsed(5).rateLimit(rateLimit).build();
            ProviderUsage same = ProviderUsage.builder()
                    .cost(MonetaryAmount.of("1.0", "usd")).creditsUsed(5).rateLimit(rateLimit).build();

            assertThat(usage).isEqualTo(same);
            assertThat(usage.hashCode()).isEqualTo(same.hashCode());
            assertThat(usage).isNotEqualTo(ProviderUsage.builder()
                    .cost(MonetaryAmount.of("2", "USD")).creditsUsed(5).rateLimit(rateLimit).build());
            assertThat(usage).isNotEqualTo(ProviderUsage.builder()
                    .cost(MonetaryAmount.of("1", "USD")).creditsUsed(6).rateLimit(rateLimit).build());
            assertThat(usage).isNotEqualTo(ProviderUsage.builder()
                    .cost(MonetaryAmount.of("1", "USD")).creditsUsed(5).build());
            assertThat(usage).isNotEqualTo(ProviderUsage.builder().build());
            assertThat(usage).isNotEqualTo(null);
            assertThat(usage).isNotEqualTo(rateLimit);
        }

        @Test
        void toStringNamesTheFields() {
            String text = ProviderUsage.builder().cost(MonetaryAmount.of("1", "USD")).creditsUsed(5).build()
                    .toString();

            assertThat(text).startsWith("ProviderUsage{").contains("1 USD").contains("creditsUsed=5");
        }
    }

    @Nested
    class RateLimitInfoTest {

        @Test
        void emptyBuilderYieldsAllOptionalsEmpty() {
            RateLimitInfo info = RateLimitInfo.builder().build();

            assertThat(info.getLimit()).isEmpty();
            assertThat(info.getRemaining()).isEmpty();
            assertThat(info.getResetAt()).isEmpty();
        }

        @Test
        void builderKeepsAllFieldsAndAcceptsZero() {
            Instant reset = Instant.parse("2025-03-01T12:00:00Z");
            RateLimitInfo info = RateLimitInfo.builder().limit(0).remaining(0).resetAt(reset).build();

            assertThat(info.getLimit()).hasValue(0);
            assertThat(info.getRemaining()).hasValue(0);
            assertThat(info.getResetAt()).contains(reset);
        }

        @Test
        void rejectsNegativeLimitAndRemaining() {
            assertThatIllegalArgumentException().isThrownBy(() -> RateLimitInfo.builder().limit(-1))
                    .withMessageContaining("limit");
            assertThatIllegalArgumentException().isThrownBy(() -> RateLimitInfo.builder().remaining(-1))
                    .withMessageContaining("remaining");
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            Instant reset = Instant.parse("2025-03-01T12:00:00Z");
            RateLimitInfo info = RateLimitInfo.builder().limit(10).remaining(3).resetAt(reset).build();
            RateLimitInfo same = RateLimitInfo.builder().limit(10).remaining(3).resetAt(reset).build();

            assertThat(info).isEqualTo(same);
            assertThat(info.hashCode()).isEqualTo(same.hashCode());
            assertThat(info).isNotEqualTo(RateLimitInfo.builder().limit(11).remaining(3).resetAt(reset).build());
            assertThat(info).isNotEqualTo(RateLimitInfo.builder().limit(10).remaining(2).resetAt(reset).build());
            assertThat(info).isNotEqualTo(
                    RateLimitInfo.builder().limit(10).remaining(3).resetAt(reset.plusSeconds(1)).build());
            assertThat(info).isNotEqualTo(RateLimitInfo.builder().limit(10).remaining(3).build());
            assertThat(info).isNotEqualTo(null);
            assertThat(info).isNotEqualTo("RateLimitInfo");
        }

        @Test
        void toStringNamesTheFields() {
            assertThat(RateLimitInfo.builder().limit(10).remaining(3).build().toString())
                    .isEqualTo("RateLimitInfo{limit=10, remaining=3, resetAt=null}");
        }
    }

    @Nested
    class SpellCorrectionTest {

        @Test
        void factoriesTrimAndKeepOptionalProviderType() {
            SpellCorrection plain = SpellCorrection.of(" corrected ", SpellCorrection.Type.DID_YOU_MEAN);
            SpellCorrection typed = SpellCorrection.of("corrected", SpellCorrection.Type.OTHER, " auto ");

            assertThat(plain.getCorrectedQuery()).isEqualTo("corrected");
            assertThat(plain.getType()).isEqualTo(SpellCorrection.Type.DID_YOU_MEAN);
            assertThat(plain.getProviderType()).isEmpty();
            assertThat(typed.getProviderType()).contains("auto");
            assertThat(SpellCorrection.of("q", SpellCorrection.Type.OTHER, "  ").getProviderType()).isEmpty();
        }

        @Test
        void rejectsBlankQueryAndMissingType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> SpellCorrection.of(" ", SpellCorrection.Type.DID_YOU_MEAN))
                    .withMessageContaining("correctedQuery");
            assertThatNullPointerException().isThrownBy(() -> SpellCorrection.of(null, SpellCorrection.Type.OTHER));
            assertThatNullPointerException().isThrownBy(() -> SpellCorrection.of("q", null));
        }

        @Test
        void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
            SpellCorrection correction = SpellCorrection.of("q", SpellCorrection.Type.SHOWING_RESULTS_FOR, "x");
            SpellCorrection same = SpellCorrection.of(" q ", SpellCorrection.Type.SHOWING_RESULTS_FOR, " x ");

            assertThat(correction).isEqualTo(same);
            assertThat(correction.hashCode()).isEqualTo(same.hashCode());
            assertThat(correction).isNotEqualTo(SpellCorrection.of("p", SpellCorrection.Type.SHOWING_RESULTS_FOR, "x"));
            assertThat(correction).isNotEqualTo(SpellCorrection.of("q", SpellCorrection.Type.NO_RESULTS_FOR, "x"));
            assertThat(correction).isNotEqualTo(SpellCorrection.of("q", SpellCorrection.Type.SHOWING_RESULTS_FOR, "y"));
            assertThat(correction).isNotEqualTo(SpellCorrection.of("q", SpellCorrection.Type.SHOWING_RESULTS_FOR));
            assertThat(correction).isNotEqualTo(null);
            assertThat(correction).isNotEqualTo("q");
        }

        @Test
        void toStringShowsTypeAndQuery() {
            assertThat(SpellCorrection.of("serp", SpellCorrection.Type.INCLUDING_RESULTS_FOR).toString())
                    .isEqualTo("SpellCorrection{INCLUDING_RESULTS_FOR: 'serp'}");
        }
    }
}
