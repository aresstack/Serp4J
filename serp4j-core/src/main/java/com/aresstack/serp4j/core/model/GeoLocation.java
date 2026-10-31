package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Geographic location of a search: a free-text name ({@code "Berlin,Germany"}) and/or coordinates. Provider
 * specific location codes (DataForSEO {@code location_code}) are provider options, not part of this type.
 */
public final class GeoLocation {

    private final String name;
    private final Double latitude;
    private final Double longitude;

    private GeoLocation(String name, Double latitude, Double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static GeoLocation named(String name) {
        return new GeoLocation(Preconditions.requireText(name, "name"), null, null);
    }

    public static GeoLocation coordinates(double latitude, double longitude) {
        return new GeoLocation(null,
                Preconditions.requireRange(latitude, -90, 90, "latitude"),
                Preconditions.requireRange(longitude, -180, 180, "longitude"));
    }

    public static GeoLocation of(String name, double latitude, double longitude) {
        return new GeoLocation(Preconditions.requireText(name, "name"),
                Preconditions.requireRange(latitude, -90, 90, "latitude"),
                Preconditions.requireRange(longitude, -180, 180, "longitude"));
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public OptionalDouble getLatitude() {
        return latitude == null ? OptionalDouble.empty() : OptionalDouble.of(latitude);
    }

    public OptionalDouble getLongitude() {
        return longitude == null ? OptionalDouble.empty() : OptionalDouble.of(longitude);
    }

    public boolean hasCoordinates() {
        return latitude != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoLocation)) {
            return false;
        }
        GeoLocation that = (GeoLocation) o;
        return Objects.equals(name, that.name)
                && Objects.equals(latitude, that.latitude)
                && Objects.equals(longitude, that.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoLocation{" + (name == null ? "" : "name='" + name + "'")
                + (latitude == null ? "" : (name == null ? "" : ", ") + "lat=" + latitude + ", lon=" + longitude) + '}';
    }
}
