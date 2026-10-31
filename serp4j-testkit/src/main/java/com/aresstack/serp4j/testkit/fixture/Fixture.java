package com.aresstack.serp4j.testkit.fixture;

import com.aresstack.serp4j.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** A loaded fixture: content plus the provenance declared in its sidecar. Immutable. */
public final class Fixture {

    private final String provider;
    private final String name;
    private final byte[] content;
    private final FixtureProvenance provenance;
    private final String source;
    private final String recordedAt;
    private final boolean anonymized;
    private final String description;
    private final String contentType;
    private final int status;

    Fixture(String provider, String name, byte[] content, FixtureProvenance provenance, String source,
            String recordedAt, boolean anonymized, String description, String contentType, int status) {
        this.provider = provider;
        this.name = name;
        this.content = content.clone();
        this.provenance = provenance;
        this.source = source;
        this.recordedAt = recordedAt;
        this.anonymized = anonymized;
        this.description = description;
        this.contentType = contentType;
        this.status = status;
    }

    /** Directory below {@code fixtures/}, normally the provider type, e.g. {@code dataforseo}. */
    public String getProvider() {
        return provider;
    }

    /** File name including extension, e.g. {@code organic-advanced.json}. */
    public String getName() {
        return name;
    }

    /** Classpath resource of the content. */
    public String getResourcePath() {
        return Fixtures.ROOT + "/" + provider + "/" + name;
    }

    public FixtureProvenance getProvenance() {
        return provenance;
    }

    public boolean isRecorded() {
        return provenance == FixtureProvenance.RECORDED;
    }

    public boolean isSynthetic() {
        return provenance == FixtureProvenance.SYNTHETIC;
    }

    /** Where a recorded fixture was taken from. */
    public Optional<String> getSource() {
        return Optional.ofNullable(source);
    }

    public Optional<String> getRecordedAt() {
        return Optional.ofNullable(recordedAt);
    }

    public boolean isAnonymized() {
        return anonymized;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public String getContentType() {
        return contentType;
    }

    /** HTTP status the fixture was answered with; default {@code 200}. */
    public int getStatus() {
        return status;
    }

    /** A copy of the raw content. */
    public byte[] getBytes() {
        return content.clone();
    }

    /** Content decoded as UTF-8. */
    public String getText() {
        return new String(content, StandardCharsets.UTF_8);
    }

    /** The fixture as HTTP response with its declared status and content type. */
    public HttpResponse toHttpResponse() {
        return toHttpResponse(status);
    }

    /** The fixture as HTTP response with the given status. */
    public HttpResponse toHttpResponse(int status) {
        return HttpResponse.builder(status).header("Content-Type", contentType).body(content).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Fixture)) {
            return false;
        }
        Fixture that = (Fixture) o;
        return provider.equals(that.provider) && name.equals(that.name) && Arrays.equals(content, that.content)
                && provenance == that.provenance;
    }

    @Override
    public int hashCode() {
        return 31 * getResourcePath().hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "Fixture{" + getResourcePath() + ", " + provenance + ", " + content.length + " bytes}";
    }
}
