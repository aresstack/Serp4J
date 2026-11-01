package com.aresstack.serp4j.testkit.selftest;

import com.aresstack.serp4j.testkit.contract.ConversationalSearchProviderContractTest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.capability.ConversationalCapabilities;
import com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider;
import com.aresstack.serp4j.core.port.conversation.SearchSessionOptions;
import com.aresstack.serp4j.testkit.FakeSharedResource;
import com.aresstack.serp4j.testkit.provider.ScriptedConversationalProvider;
import org.junit.jupiter.api.Nested;

/** The conversational mock passes the dialog contract, once not resumable with a turn limit, once resumable. */
class ScriptedConversationalProviderContractTest {

    private static final SearchTarget AI_MODE = SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.AI_MODE);

    @Nested
    class NotResumableWithTurnLimit extends ConversationalSearchProviderContractTest<FakeSharedResource> {

        @Override
        protected FakeSharedResource createInfrastructure() {
            return new FakeSharedResource("browser-runtime");
        }

        @Override
        protected ConversationalSearchProvider createProvider(ProviderId id, FakeSharedResource infrastructure) {
            return ScriptedConversationalProvider.builder(id)
                    .infrastructure(infrastructure)
                    .capabilities(ConversationalCapabilities.builder().maxTurns(3).build())
                    .build();
        }

        @Override
        protected SearchSessionOptions sessionOptions() {
            return SearchSessionOptions.builder(AI_MODE).build();
        }
    }

    @Nested
    class Resumable extends ConversationalSearchProviderContractTest<FakeSharedResource> {

        @Override
        protected FakeSharedResource createInfrastructure() {
            return new FakeSharedResource("browser-runtime");
        }

        @Override
        protected ConversationalSearchProvider createProvider(ProviderId id, FakeSharedResource infrastructure) {
            return ScriptedConversationalProvider.builder(id)
                    .infrastructure(infrastructure)
                    .capabilities(ConversationalCapabilities.builder().resumable(true).maxConcurrentSessions(2)
                            .build())
                    .build();
        }

        @Override
        protected SearchSessionOptions sessionOptions() {
            return SearchSessionOptions.builder(AI_MODE).build();
        }
    }
}
