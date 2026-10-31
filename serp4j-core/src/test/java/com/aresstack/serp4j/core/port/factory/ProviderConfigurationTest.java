package com.aresstack.serp4j.core.port.factory;

import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.StaticCredentialProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ProviderConfigurationTest {

    @Test
    void idDefaultsToProviderTypeAndPropertiesAreTyped() {
        ProviderConfiguration configuration = ProviderConfiguration.builder("brightdata")
                .id(ProviderId.of("brightdata-de"))
                .property("zone", "serp_de")
                .property("timeout", "PT30S")
                .property("retries", "3")
                .property("verbose", "TRUE")
                .build();

        assertThat(ProviderConfiguration.of("brave").getId()).isEqualTo(ProviderId.of("brave"));
        assertThat(configuration.getId()).isEqualTo(ProviderId.of("brightdata-de"));
        assertThat(configuration.getRequired("zone")).isEqualTo("serp_de");
        assertThat(configuration.getDuration("timeout")).contains(Duration.ofSeconds(30));
        assertThat(configuration.getInt("retries")).hasValue(3);
        assertThat(configuration.getBoolean("verbose")).contains(true);
        assertThat(configuration.get("missing")).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> configuration.getRequired("missing"));
        assertThatIllegalStateException().isThrownBy(() ->
                configuration.toBuilder().property("retries", "many").build().getInt("retries"));
        assertThat(configuration.toString()).doesNotContain("serp_de");
    }

    @Test
    void credentialKeyFollowsInstanceIdUnlessOverridden() {
        ProviderConfiguration plain = ProviderConfiguration.builder("brightdata")
                .id(ProviderId.of("brightdata-de")).build();
        ProviderConfiguration shared = plain.toBuilder().property("credential.password", "brightdata.password").build();

        assertThat(plain.credentialKey("password")).isEqualTo(CredentialKey.of("brightdata-de.password"));
        assertThat(shared.credentialKey("password")).isEqualTo(CredentialKey.of("brightdata.password"));
    }

    @Test
    void environmentOffersTypedServicesCredentialsAndClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        CredentialProvider credentials = StaticCredentialProvider.of(CredentialKey.of("k"), "v");
        Runnable service = () -> { };
        ProviderEnvironment environment = ProviderEnvironment.builder()
                .clock(fixed).credentials(credentials).service(Runnable.class, service).build();

        assertThat(environment.getClock()).isSameAs(fixed);
        assertThat(environment.getCredentials()).isSameAs(credentials);
        assertThat(environment.getService(Runnable.class)).contains(service);
        assertThat(environment.requireService(Runnable.class)).isSameAs(service);
        assertThat(environment.getService(Comparable.class)).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> environment.requireService(Comparable.class));
        assertThat(ProviderEnvironment.builder().build().getCredentials().resolve(CredentialKey.of("k"))).isEmpty();
    }

    @Test
    void providerTypeIsANormalisedCode() {
        assertThat(ProviderConfiguration.of(" Brave ").getProviderType()).isEqualTo("brave");
        assertThatIllegalArgumentException().isThrownBy(() -> ProviderConfiguration.of("not valid"));
    }
}
