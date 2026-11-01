package com.aresstack.serp4j.http.ahc;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AhcHttpTransportOptionsTest {

    @Test
    void defaultsMatchAskAiTransportSettingsWithNeutralUserAgent() {
        AhcHttpTransportOptions options = AhcHttpTransportOptions.defaults();

        assertThat(options.getConnectTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(options.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.getMaxConnections()).isEqualTo(100);
        assertThat(options.getMaxConnectionsPerHost()).isEqualTo(20);
        assertThat(options.isFollowRedirects()).isTrue();
        assertThat(options.getMaxRedirects()).isEqualTo(5);
        assertThat(options.isKeepAlive()).isTrue();
        assertThat(options.isCompressionEnabled()).isTrue();
        assertThat(options.getUserAgent()).startsWith("Serp4J/").doesNotContain("AskAI");
    }

    @Test
    void builderRoundTripsAndValueSemantics() {
        AhcHttpTransportOptions options = AhcHttpTransportOptions.builder()
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(3))
                .maxConnections(10)
                .maxConnectionsPerHost(5)
                .followRedirects(false)
                .maxRedirects(0)
                .keepAlive(false)
                .compressionEnabled(false)
                .userAgent("  Host/1  ")
                .build();

        assertThat(options.getUserAgent()).isEqualTo("Host/1");
        assertThat(options.toBuilder().build()).isEqualTo(options).hasSameHashCodeAs(options);
        assertThat(options).isNotEqualTo(AhcHttpTransportOptions.defaults());
        assertThat(options.toString()).contains("maxConnections=10", "userAgent=Host/1");
    }

    @Test
    void rejectsInvalidValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .connectTimeout(Duration.ZERO).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .requestTimeout(Duration.ofMillis(-1)).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .readTimeout(Duration.ofDays(30)).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .maxConnections(0).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .maxConnections(5).maxConnectionsPerHost(6).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .maxRedirects(-1).build());
        assertThatIllegalArgumentException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .userAgent(" ").build());
        assertThatNullPointerException().isThrownBy(() -> AhcHttpTransportOptions.builder()
                .readTimeout(null).build());
    }
}
