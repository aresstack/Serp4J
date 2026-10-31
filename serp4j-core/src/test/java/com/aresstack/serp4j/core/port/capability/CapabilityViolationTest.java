package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Value-type tests for {@link CapabilityViolation} (Nachtrag A.1 #14, WP2 §6). */
class CapabilityViolationTest {

    private final SearchTarget target = SearchTarget.web(SearchEngineId.GOOGLE);

    @Test
    void featureViolationCarriesTheRequestsRequirementLevel() {
        CapabilityViolation optional = CapabilityViolation.ofFeature(SearchFeature.FRESHNESS,
                RequirementLevel.OPTIONAL, " freshness not supported ");
        CapabilityViolation required = CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.REQUIRED, "device not supported");

        assertThat(optional.getFeature()).contains(SearchFeature.FRESHNESS);
        assertThat(optional.getTarget()).isEmpty();
        assertThat(optional.getVertical()).isEmpty();
        assertThat(optional.getLevel()).isEqualTo(RequirementLevel.OPTIONAL);
        assertThat(optional.isRequired()).isFalse();
        assertThat(optional.getMessage()).isEqualTo("freshness not supported");
        assertThat(required.isRequired()).isTrue();
    }

    @Test
    void targetAndVerticalViolationsAreAlwaysRequired() {
        CapabilityViolation byTarget = CapabilityViolation.ofTarget(target, "target not supported");
        CapabilityViolation byVertical = CapabilityViolation.ofVertical(SearchVertical.IMAGES, "no images");

        assertThat(byTarget.getTarget()).contains(target);
        assertThat(byTarget.getFeature()).isEmpty();
        assertThat(byTarget.getVertical()).isEmpty();
        assertThat(byTarget.getLevel()).isEqualTo(RequirementLevel.REQUIRED);
        assertThat(byTarget.isRequired()).isTrue();
        assertThat(byVertical.getVertical()).contains(SearchVertical.IMAGES);
        assertThat(byVertical.getFeature()).isEmpty();
        assertThat(byVertical.getTarget()).isEmpty();
        assertThat(byVertical.isRequired()).isTrue();
    }

    @Test
    void rejectsMissingSubjectLevelAndBlankMessage() {
        assertThatNullPointerException()
                .isThrownBy(() -> CapabilityViolation.ofFeature(null, RequirementLevel.REQUIRED, "m"))
                .withMessageContaining("feature");
        assertThatNullPointerException()
                .isThrownBy(() -> CapabilityViolation.ofFeature(SearchFeature.DEVICE, null, "m"))
                .withMessageContaining("level");
        assertThatNullPointerException().isThrownBy(() -> CapabilityViolation.ofTarget(null, "m"))
                .withMessageContaining("target");
        assertThatNullPointerException().isThrownBy(() -> CapabilityViolation.ofVertical(null, "m"))
                .withMessageContaining("vertical");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CapabilityViolation.ofFeature(SearchFeature.DEVICE, RequirementLevel.REQUIRED, " "))
                .withMessageContaining("message");
        assertThatNullPointerException().isThrownBy(() -> CapabilityViolation.ofTarget(target, null))
                .withMessageContaining("message");
    }

    @Test
    void equalsAndHashCodeCoverEveryFieldIncludingInequality() {
        CapabilityViolation violation = CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.OPTIONAL, "device not supported");

        assertThat(violation).isEqualTo(CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.OPTIONAL, " device not supported "));
        assertThat(violation.hashCode()).isEqualTo(CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.OPTIONAL, "device not supported").hashCode());
        assertThat(violation).isNotEqualTo(CapabilityViolation.ofFeature(SearchFeature.FRESHNESS,
                RequirementLevel.OPTIONAL, "device not supported"));
        assertThat(violation).isNotEqualTo(CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.REQUIRED, "device not supported"));
        assertThat(violation).isNotEqualTo(CapabilityViolation.ofFeature(SearchFeature.DEVICE,
                RequirementLevel.OPTIONAL, "other message"));
        assertThat(violation).isNotEqualTo(CapabilityViolation.ofTarget(target, "device not supported"));
        assertThat(violation).isNotEqualTo(
                CapabilityViolation.ofVertical(SearchVertical.WEB, "device not supported"));
        assertThat(CapabilityViolation.ofTarget(target, "m"))
                .isEqualTo(CapabilityViolation.ofTarget(SearchTarget.web(SearchEngineId.GOOGLE), "m"));
        assertThat(CapabilityViolation.ofTarget(target, "m"))
                .isNotEqualTo(CapabilityViolation.ofTarget(SearchTarget.web(SearchEngineId.BRAVE), "m"));
        assertThat(CapabilityViolation.ofVertical(SearchVertical.IMAGES, "m"))
                .isNotEqualTo(CapabilityViolation.ofVertical(SearchVertical.NEWS, "m"));
        assertThat(violation).isNotEqualTo(null);
        assertThat(violation).isNotEqualTo(SearchFeature.DEVICE);
    }

    @Test
    void toStringNamesLevelSubjectAndMessage() {
        assertThat(CapabilityViolation.ofFeature(SearchFeature.DEVICE, RequirementLevel.OPTIONAL, "msg").toString())
                .isEqualTo("CapabilityViolation{OPTIONAL DEVICE: msg}");
        assertThat(CapabilityViolation.ofTarget(target, "msg").toString())
                .isEqualTo("CapabilityViolation{REQUIRED google/web: msg}");
        assertThat(CapabilityViolation.ofVertical(SearchVertical.IMAGES, "msg").toString())
                .contains("REQUIRED").contains("images").contains("msg");
    }
}
