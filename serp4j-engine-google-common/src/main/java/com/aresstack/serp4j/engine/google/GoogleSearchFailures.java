package com.aresstack.serp4j.engine.google;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.id.ProviderId;

/** Typed failures shared by the Google providers (ADR-016: obstacles are reported, never bypassed). */
public final class GoogleSearchFailures {

    /** Detail key of {@link SearchProviderException}: the {@link GoogleObstacle} name. */
    public static final String DETAIL_OBSTACLE = "google_obstacle";

    private GoogleSearchFailures() {
    }

    /**
     * @param provider provider id
     * @param obstacle obstacle other than {@link GoogleObstacle#NONE}
     * @param path     access path for the message ({@code "HTTP"} or {@code "browser"})
     * @return {@link SearchErrorCategory#BLOCKED}, not retryable
     */
    public static SearchProviderException obstacle(ProviderId provider, GoogleObstacle obstacle, String path) {
        String message;
        switch (obstacle) {
            case CONSENT:
                message = "Google shows its consent page instead of results; Serp4J does not answer consent dialogs";
                break;
            case JAVASCRIPT_REQUIRED:
                message = "Google demands JavaScript and returns no results";
                break;
            case CHALLENGE:
                message = "Google shows a challenge (unusual traffic / CAPTCHA); Serp4J never solves it";
                break;
            case LOGIN:
                message = "Google demands a sign-in; Serp4J never signs in";
                break;
            default:
                message = "Google blocked the search";
        }
        return SearchProviderException.builder(provider, SearchErrorCategory.BLOCKED,
                        message + " (" + path + " access path)")
                .retryable(false)
                .detail(DETAIL_OBSTACLE, obstacle.name())
                .build();
    }

    /**
     * Classifies a non-200 HTTP status of the result page: 429 {@code RATE_LIMITED}, 403 {@code BLOCKED}, 5xx
     * {@code UNAVAILABLE}, other 4xx {@code RESPONSE_INVALID}.
     *
     * @return the failure, or {@code null} for statuses below 400
     */
    public static SearchProviderException forStatus(ProviderId provider, int status) {
        SearchErrorCategory category;
        if (status == 429) {
            category = SearchErrorCategory.RATE_LIMITED;
        } else if (status == 403) {
            category = SearchErrorCategory.BLOCKED;
        } else if (status >= 500) {
            category = SearchErrorCategory.UNAVAILABLE;
        } else if (status >= 400) {
            category = SearchErrorCategory.RESPONSE_INVALID;
        } else {
            return null;
        }
        return SearchProviderException.builder(provider, category, "Google answered HTTP " + status)
                .providerStatusCode(status).build();
    }

    /** The page has neither results nor a known obstacle: the Google layout probably changed. */
    public static SearchProviderException unrecognized(ProviderId provider, String path) {
        return SearchProviderException.builder(provider, SearchErrorCategory.RESPONSE_INVALID,
                "Google result page not recognised (" + path + " access path); the page layout may have changed")
                .build();
    }
}
