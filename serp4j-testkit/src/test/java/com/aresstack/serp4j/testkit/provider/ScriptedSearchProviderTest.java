package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchContext;
import org.junit.jupiter.api.Test;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static org.assertj.core.api.Assertions.assertThat;

class ScriptedSearchProviderTest {

    private static final ProviderId ID = ProviderId.of("scripted");
    private static final SearchRequest REQUEST = SearchRequest.builder("serp4j").build();

    @Test
    void playsTheScriptInOrderAndThenFailsLoudly() {
        ScriptedSearchProvider provider = ScriptedSearchProvider.builder(ID)
                .then(Outcome.failure(SearchErrorCategory.TIMEOUT), Outcome.failure(SearchErrorCategory.UNAVAILABLE))
                .then(Outcome.syntheticResults(2))
                .build();

        assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.TIMEOUT);
        assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.UNAVAILABLE);
        assertThat(provider.getRemainingSteps()).isEqualTo(1);
        assertThat(assertCompletes(provider.search(REQUEST, SearchContext.defaults())).getData().getResults())
                .hasSize(2);
        assertThat(assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.INTERNAL)
                .getMessage()).contains("exhausted");
        assertThat(provider.getSearchCount()).isEqualTo(4);
    }

    @Test
    void thenAlwaysAnswersAfterTheScript() {
        ScriptedSearchProvider provider = ScriptedSearchProvider.builder(ID)
                .then(Outcome.failure(SearchErrorCategory.RATE_LIMITED))
                .thenAlways(Outcome.empty())
                .build();

        assertFailsWith(provider.search(REQUEST, SearchContext.defaults()), SearchErrorCategory.RATE_LIMITED);
        assertCompletes(provider.search(REQUEST, SearchContext.defaults()));
        assertCompletes(provider.search(REQUEST, SearchContext.defaults()));
    }

    @Test
    void rejectedCallsDoNotConsumeSteps() {
        ScriptedSearchProvider provider = ScriptedSearchProvider.builder(ID).then(Outcome.empty()).build();
        SearchRequest unsupported = REQUEST.toBuilder().require(SearchFeature.ASYNC_TASK).build();

        assertFailsWith(provider.search(unsupported, SearchContext.defaults()), SearchErrorCategory.UNSUPPORTED);

        assertThat(provider.getRemainingSteps()).isEqualTo(1);
        assertCompletes(provider.search(REQUEST, SearchContext.defaults()));
    }
}
