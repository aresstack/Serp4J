package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.error.SearchErrorCategory;

/**
 * Interpretation of the internal DataForSEO status codes in the JSON body (API reference §10). The HTTP status is
 * not enough: DataForSEO usually answers HTTP 200 and reports errors only in {@code status_code}.
 */
public final class DataForSeoStatus {

    /** Request successful. */
    public static final int OK = 20000;
    /** Task created (standard flow). */
    public static final int TASK_CREATED = 20100;
    /** No search results: an empty result, not a parser error. */
    public static final int NO_RESULTS = 40102;
    /** Task finished with partial results. */
    public static final int PARTIAL_RESULTS = 40106;

    private DataForSeoStatus() {
    }

    /** {@code true} for codes that carry a usable (possibly empty or partial) result. */
    public static boolean isSuccess(int code) {
        return code == OK || code == TASK_CREATED || code == NO_RESULTS || code == PARTIAL_RESULTS;
    }

    /** Error category for a non-success code. */
    public static SearchErrorCategory categoryOf(int code) {
        switch (code) {
            case 40100:
                return SearchErrorCategory.AUTHENTICATION;
            case 40104:
            case 40207:
                return SearchErrorCategory.CONFIGURATION;
            case 40200:
            case 40203:
            case 40210:
                return SearchErrorCategory.QUOTA_EXHAUSTED;
            case 40202:
            case 40209:
                return SearchErrorCategory.RATE_LIMITED;
            case 40101:
            case 40103:
            case 50000:
            case 50301:
                return SearchErrorCategory.UNAVAILABLE;
            case 50401:
                return SearchErrorCategory.TIMEOUT;
            default:
                if (code >= 50000 && code < 60000) {
                    return SearchErrorCategory.UNAVAILABLE;
                }
                if (code >= 40000 && code < 50000) {
                    // 40006, 40105, 40401, 40403, 405xx and unknown 4xxxx: the request needs correcting.
                    return SearchErrorCategory.INVALID_REQUEST;
                }
                return SearchErrorCategory.RESPONSE_INVALID;
        }
    }

    /** Whether a retry with backoff may succeed (40103 task failed, rate limits, 5xxxx). */
    public static boolean isRetryable(int code) {
        return code == 40103 || categoryOf(code).isRetryable();
    }

    /** Category for an HTTP error status whose body carries no DataForSEO status. */
    public static SearchErrorCategory categoryOfHttp(int httpStatus) {
        switch (httpStatus) {
            case 401:
            case 403:
                return SearchErrorCategory.AUTHENTICATION;
            case 402:
                return SearchErrorCategory.QUOTA_EXHAUSTED;
            case 404:
                return SearchErrorCategory.CONFIGURATION;
            case 429:
                return SearchErrorCategory.RATE_LIMITED;
            case 408:
            case 504:
                return SearchErrorCategory.TIMEOUT;
            default:
                return httpStatus >= 500 ? SearchErrorCategory.UNAVAILABLE : SearchErrorCategory.INVALID_REQUEST;
        }
    }
}
