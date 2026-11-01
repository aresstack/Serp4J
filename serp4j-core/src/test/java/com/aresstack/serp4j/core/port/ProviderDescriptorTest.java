package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link ProviderDescriptor} (Nachtrag A.1 #13: equality including inequality). */
class ProviderDescriptorTest {

    private final SearchCapabilities capabilities = SearchCapabilities.builder()
            .target(SearchEngineId.BRAVE, SearchVertical.WEB).feature(SearchFeature.COUNTRY).build();

    private ProviderDescriptor.Builder descriptor() {
        return ProviderDescriptor.builder(ProviderId.of("brave-de"), "brave", ProviderKind.CLOUD_API, capabilities)
                .displayName("Brave DE").attribute("vendorUrl", "https://brave.com");
    }

    @Test
    void displayNameDefaultsToIdAndProviderTypeIsNormalised() {
        ProviderDescriptor plain = ProviderDescriptor.builder(ProviderId.of("brave-de"), " Brave ",
                ProviderKind.CLOUD_API, capabilities).build();

        assertThat(plain.getDisplayName()).isEqualTo("brave-de");
        assertThat(plain.getProviderType()).isEqualTo("brave");
        assertThat(plain.getAttributes()).isEmpty();
        assertThat(descriptor().displayName("  ").build().getDisplayName()).isEqualTo("brave-de");
    }

    @Test
    void rejectsMissingMandatoryFieldsAndMalformedProviderType() {
        assertThatNullPointerException()
                .isThrownBy(() -> ProviderDescriptor.builder(null, "brave", ProviderKind.CLOUD_API, capabilities));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProviderDescriptor.builder(ProviderId.of("x"), "br ave", ProviderKind.CLOUD_API,
                        capabilities));
        assertThatNullPointerException()
                .isThrownBy(() -> ProviderDescriptor.builder(ProviderId.of("x"), "brave", null, capabilities));
        assertThatNullPointerException()
                .isThrownBy(() -> ProviderDescriptor.builder(ProviderId.of("x"), "brave", ProviderKind.CLOUD_API,
                        null));
        assertThatIllegalArgumentException().isThrownBy(() -> descriptor().attribute(" ", "v"));
        assertThatNullPointerException().isThrownBy(() -> descriptor().attribute("k", null));
    }

    @Test
    void attributesAreUnmodifiableAndToBuilderRoundTrips() {
        ProviderDescriptor built = descriptor().build();

        assertThatThrownBy(() -> built.getAttributes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(built.toBuilder().build()).isEqualTo(built);
        assertThat(built.toBuilder().build().hashCode()).isEqualTo(built.hashCode());
        assertThat(built.toBuilder().attribute("pricingModel", "credits").build().getAttributes())
                .containsEntry("vendorUrl", "https://brave.com").containsEntry("pricingModel", "credits");
        assertThat(built.getAttributes()).hasSize(1);
    }

    @Test
    void equalDescriptorsAreEqualAndEveryDifferingFieldBreaksEquality() {
        ProviderDescriptor built = descriptor().build();
        SearchCapabilities otherCapabilities = capabilities.toBuilder().feature(SearchFeature.LANGUAGE).build();

        assertThat(built).isEqualTo(descriptor().build());
        assertThat(built.hashCode()).isEqualTo(descriptor().build().hashCode());
        assertThat(built).isNotEqualTo(ProviderDescriptor.builder(ProviderId.of("brave-us"), "brave",
                ProviderKind.CLOUD_API, capabilities).displayName("Brave DE")
                .attribute("vendorUrl", "https://brave.com").build());
        assertThat(built).isNotEqualTo(ProviderDescriptor.builder(ProviderId.of("brave-de"), "other",
                ProviderKind.CLOUD_API, capabilities).displayName("Brave DE")
                .attribute("vendorUrl", "https://brave.com").build());
        assertThat(built).isNotEqualTo(descriptor().displayName("Brave Germany").build());
        assertThat(built).isNotEqualTo(ProviderDescriptor.builder(ProviderId.of("brave-de"), "brave",
                ProviderKind.MOCK, capabilities).displayName("Brave DE")
                .attribute("vendorUrl", "https://brave.com").build());
        assertThat(built).isNotEqualTo(ProviderDescriptor.builder(ProviderId.of("brave-de"), "brave",
                ProviderKind.CLOUD_API, otherCapabilities).displayName("Brave DE")
                .attribute("vendorUrl", "https://brave.com").build());
        assertThat(built).isNotEqualTo(descriptor().attribute("pricingModel", "credits").build());
        assertThat(built).isNotEqualTo(null);
        assertThat(built).isNotEqualTo(capabilities);
    }

    @Test
    void toStringNamesIdTypeNameAndKindButNoAttributes() {
        assertThat(descriptor().attribute("internalNote", "value-not-shown").build().toString())
                .isEqualTo("ProviderDescriptor{id=brave-de, providerType='brave', displayName='Brave DE', "
                        + "kind=CLOUD_API}");
    }
}
