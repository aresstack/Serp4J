package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link SearchCapabilities#check}: all violations, split into required ones (the provider cannot serve
 * the request) and optional ones (the provider ignores the aspect and records a warning).
 */
public final class CapabilityCheck {

    private static final CapabilityCheck SUPPORTED = new CapabilityCheck(Collections.<CapabilityViolation>emptyList());

    private final List<CapabilityViolation> violations;

    private CapabilityCheck(List<CapabilityViolation> violations) {
        this.violations = violations;
    }

    public static CapabilityCheck supported() {
        return SUPPORTED;
    }

    public static CapabilityCheck of(Collection<CapabilityViolation> violations) {
        List<CapabilityViolation> copy = Preconditions.immutableList(violations, "violations");
        return copy.isEmpty() ? SUPPORTED : new CapabilityCheck(copy);
    }

    /** {@code true} when no {@link CapabilityViolation#isRequired() required} aspect is violated. */
    public boolean isSupported() {
        for (CapabilityViolation violation : violations) {
            if (violation.isRequired()) {
                return false;
            }
        }
        return true;
    }

    /** All violations, required and optional, in check order. */
    public List<CapabilityViolation> getViolations() {
        return violations;
    }

    public List<CapabilityViolation> getRequiredViolations() {
        return filter(true);
    }

    public List<CapabilityViolation> getOptionalViolations() {
        return filter(false);
    }

    private List<CapabilityViolation> filter(boolean required) {
        List<CapabilityViolation> matching = new ArrayList<CapabilityViolation>();
        for (CapabilityViolation violation : violations) {
            if (violation.isRequired() == required) {
                matching.add(violation);
            }
        }
        return Collections.unmodifiableList(matching);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CapabilityCheck && violations.equals(((CapabilityCheck) o).violations));
    }

    @Override
    public int hashCode() {
        return violations.hashCode();
    }

    @Override
    public String toString() {
        return "CapabilityCheck{supported=" + isSupported() + ", violations=" + violations + '}';
    }
}
