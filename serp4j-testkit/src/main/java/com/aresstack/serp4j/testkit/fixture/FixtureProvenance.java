package com.aresstack.serp4j.testkit.fixture;

/** Where a fixture's content comes from (decision E-1). */
public enum FixtureProvenance {
    /** Recorded from the real provider and anonymized. The only kind response mappers may be tested against. */
    RECORDED,
    /** Written by hand or generated for tests; never presented as a provider answer. */
    SYNTHETIC
}
