package com.aresstack.serp4j.json;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.SearchConversation;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Serp4jJsonTest {

    private static final Serp4jJson JSON = Serp4jJson.create();
    private static final Serp4jJson WITH_RAW = Serp4jJson.builder().includeRawPayload(true).build();

    @Nested
    class RoundTrip {

        @Test
        void full_search_response_with_raw_payload_opt_in() throws Exception {
            SearchResponse response = ModelSamples.fullResponse();
            String json = WITH_RAW.write(response);
            assertThat(WITH_RAW.readSearchResponse(json)).isEqualTo(response);
            assertThat(JSON.readSearchResponse(new StringReader(json))).isEqualTo(response);
            assertThat(JsonTrees.read(json).path("metadata", "rawPayload", "body")).isPresent();
        }

        @Test
        void full_search_response_without_raw_payload_by_default() {
            SearchResponse response = ModelSamples.fullResponse();
            String json = JSON.write(response);
            assertThat(json).doesNotContain("rawPayload");
            assertThat(JSON.readSearchResponse(json)).isEqualTo(response.withoutRawPayload());
        }

        @Test
        void minimal_and_merged_responses() {
            SearchResponse minimal = SearchResponse.builder(SearchData.empty()).build();
            assertThat(JSON.readSearchResponse(JSON.write(minimal))).isEqualTo(minimal);
            assertThat(JSON.write(minimal)).isEqualTo("{\"formatVersion\":1,\"type\":\"search_response\",\"data\":{}}");

            SearchResponse merged = ModelSamples.mergedResponse();
            SearchResponse read = JSON.readSearchResponse(JSON.write(merged));
            assertThat(read).isEqualTo(merged);
            assertThat(read.getMetadata().get().isMergedResponse()).isTrue();
        }

        @Test
        void search_conversation() throws Exception {
            SearchConversation conversation = ModelSamples.conversation();
            assertThat(WITH_RAW.readSearchConversation(WITH_RAW.write(conversation))).isEqualTo(conversation);
            assertThat(JSON.readSearchConversation(new StringReader(JSON.write(conversation))))
                    .isEqualTo(withoutRawPayload(conversation));
        }

        @Test
        void session_handle() throws Exception {
            SessionHandle handle = ModelSamples.sessionHandle();
            String json = JSON.write(handle);
            assertThat(json).isEqualTo("{\"formatVersion\":1,\"type\":\"session_handle\",\"providerId\":\"dataforseo\","
                    + "\"sessionId\":\"session-1\",\"opaqueToken\":\"opaque-token-123\"}");
            assertThat(JSON.readSessionHandle(json)).isEqualTo(handle);
            assertThat(JSON.readSessionHandle(new StringReader(json))).isEqualTo(handle);
        }

        @Test
        void pretty_output_reads_back() {
            Serp4jJson pretty = Serp4jJson.builder().prettyPrint(true).build();
            String json = pretty.write(ModelSamples.fullResponse());
            assertThat(json).startsWith("{\n  \"formatVersion\": 1,\n  \"type\": \"search_response\",");
            assertThat(pretty.readSearchResponse(json)).isEqualTo(ModelSamples.fullResponse().withoutRawPayload());
        }

        @Test
        void written_document_is_stable() {
            SearchResponse response = ModelSamples.fullResponse();
            String first = WITH_RAW.write(response);
            assertThat(WITH_RAW.write(WITH_RAW.readSearchResponse(first))).isEqualTo(first);
        }

        private SearchConversation withoutRawPayload(SearchConversation conversation) {
            SearchConversation.Builder builder = SearchConversation.builder(conversation.getSessionId(),
                    conversation.getProviderId(), conversation.getTarget(), conversation.getState());
            for (ConversationTurn turn : conversation.getTurns()) {
                builder.turn(ConversationTurn.of(turn.getIndex(), turn.getRequest(),
                        turn.getResponse().withoutRawPayload(), turn.getStartedAt(), turn.getCompletedAt()));
            }
            return builder.build();
        }
    }

    @Nested
    class FormatVersion {

        @Test
        void missing_version_is_rejected() {
            assertThatThrownBy(() -> JSON.readSessionHandle(
                    "{\"type\":\"session_handle\",\"providerId\":\"p\",\"sessionId\":\"s\",\"opaqueToken\":\"t\"}"))
                    .isInstanceOfSatisfying(UnsupportedFormatVersionException.class,
                            e -> assertThat(e.getFormatVersion()).isEmpty())
                    .hasMessageContaining("missing");
            assertThatThrownBy(() -> JSON.readSearchResponse("{\"formatVersion\":null,\"data\":{}}"))
                    .isInstanceOf(UnsupportedFormatVersionException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"1\"", "1.5", "true", "{}", "99999999999"})
        void non_integer_version_is_rejected(String version) {
            assertThatThrownBy(() -> JSON.readSearchResponse(
                    "{\"formatVersion\":" + version + ",\"type\":\"search_response\",\"data\":{}}"))
                    .isInstanceOfSatisfying(UnsupportedFormatVersionException.class,
                            e -> assertThat(e.getFormatVersion()).isEmpty());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 2, 99})
        void unknown_or_newer_version_is_rejected(int version) {
            assertThatThrownBy(() -> JSON.readSearchResponse(
                    "{\"formatVersion\":" + version + ",\"type\":\"search_response\",\"data\":{}}"))
                    .isInstanceOfSatisfying(UnsupportedFormatVersionException.class,
                            e -> assertThat(e.getFormatVersion()).hasValue(version))
                    .hasMessageContaining("unsupported version " + version);
        }

        @Test
        void supported_version_written_as_decimal_is_accepted() {
            assertThat(JSON.readSearchResponse("{\"formatVersion\":1.0,\"type\":\"search_response\",\"data\":{}}"))
                    .isEqualTo(SearchResponse.builder(SearchData.empty()).build());
        }

        @Test
        void writer_uses_current_version_which_is_supported() {
            assertThat(Serp4jJson.SUPPORTED_FORMAT_VERSIONS).contains(Serp4jJson.CURRENT_FORMAT_VERSION);
            assertThat(JsonTrees.read(JSON.write(ModelSamples.sessionHandle())).get("formatVersion"))
                    .hasValue(StructuredValue.of(Serp4jJson.CURRENT_FORMAT_VERSION));
        }
    }

    @Nested
    class Tolerance {

        @Test
        void unknown_members_are_ignored_at_every_level() {
            String json = "{\"formatVersion\":1,\"type\":\"search_response\",\"future\":{\"x\":[1]},"
                    + "\"data\":{\"results\":[{\"id\":\"r1\",\"type\":\"organic\",\"url\":\"https://a.example\","
                    + "\"newField\":true,\"position\":{\"rankInGroup\":1,\"area\":\"MAIN\",\"zone\":\"x\"}}],"
                    + "\"features\":[{\"kind\":\"related_queries\",\"id\":\"f\",\"queries\":[{\"text\":\"q\","
                    + "\"score\":1}],\"layout\":\"grid\"}],\"other\":null},"
                    + "\"metadata\":{\"provider\":\"brave\",\"engine\":\"brave\",\"vertical\":\"web\","
                    + "\"query\":{\"originalQuery\":\"q\",\"intentHint\":\"x\"},\"latencyBucket\":\"fast\"}}";
            SearchResponse response = JSON.readSearchResponse(json);
            assertThat(response.getData().getResults()).hasSize(1);
            assertThat(response.getData().getFeatures()).hasSize(1);
            assertThat(response.getMetadata().get().getEngine()).hasValue(SearchEngineId.BRAVE);
        }

        @Test
        void null_members_count_as_absent() {
            String json = "{\"formatVersion\":1,\"type\":\"search_response\",\"data\":{\"results\":null},"
                    + "\"metadata\":null,\"analysis\":null}";
            assertThat(JSON.readSearchResponse(json)).isEqualTo(SearchResponse.builder(SearchData.empty()).build());
        }

        @Test
        void raw_payload_in_document_is_always_read() {
            String json = WITH_RAW.write(ModelSamples.fullResponse());
            assertThat(JSON.readSearchResponse(json).getMetadata().get().getRawPayload())
                    .hasValue(ModelSamples.rawPayload());
        }
    }

    @Nested
    class Errors {

        @Test
        void wrong_document_type_is_rejected() {
            String handle = JSON.write(ModelSamples.sessionHandle());
            assertThatThrownBy(() -> JSON.readSearchResponse(handle))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessageContaining("expected 'search_response' but found 'session_handle'");
        }

        @Test
        void missing_required_member_names_its_path() {
            assertThatThrownBy(() -> JSON.readSearchResponse("{\"formatVersion\":1,\"type\":\"search_response\","
                    + "\"data\":{\"results\":[{\"id\":\"r1\",\"type\":\"organic\"}]}}"))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessage("$.data.results[0].url: required member is missing");
        }

        @Test
        void wrong_value_type_names_its_path() {
            assertThatThrownBy(() -> JSON.readSessionHandle("{\"formatVersion\":1,\"type\":\"session_handle\","
                    + "\"providerId\":1,\"sessionId\":\"s\",\"opaqueToken\":\"t\"}"))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessage("$.providerId: expected a string but found number");
        }

        @Test
        void unknown_feature_kind_and_enum_value_are_rejected() {
            assertThatThrownBy(() -> JSON.readSearchResponse("{\"formatVersion\":1,\"type\":\"search_response\","
                    + "\"data\":{\"features\":[{\"id\":\"f\",\"kind\":\"hologram\"}]}}"))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessageContaining("$.data.features[0].kind: unknown feature kind 'hologram'");
            assertThatThrownBy(() -> JSON.readSearchConversation("{\"formatVersion\":1,"
                    + "\"type\":\"search_conversation\",\"sessionId\":\"s\",\"providerId\":\"p\","
                    + "\"target\":{\"engine\":\"google\",\"vertical\":\"ai_mode\"},\"state\":\"SLEEPING\"}"))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessageContaining("unknown SessionState 'SLEEPING'");
        }

        @Test
        void invalid_json_text_and_non_object_documents_are_rejected() {
            assertThatThrownBy(() -> JSON.readSearchResponse("{")).isInstanceOf(JsonTreeException.class);
            assertThatThrownBy(() -> JSON.readSearchResponse("[]")).isInstanceOf(Serp4jJsonException.class);
        }

        @Test
        void extension_of_unsupported_type_fails_instead_of_being_dropped() {
            Extensions extensions = Extensions.builder()
                    .put(ExtensionKey.of("acme", "when", java.time.LocalDate.class), java.time.LocalDate.of(2026, 11, 1))
                    .build();
            SearchResponse response = SearchResponse.of(SearchData.empty(),
                    SearchMetadata.forProvider(ModelSamples.BRAVE, SearchEngineId.BRAVE, SearchVertical.WEB,
                            QueryInfo.of("q")).extensions(extensions).build());
            assertThatThrownBy(() -> JSON.write(response))
                    .isInstanceOf(Serp4jJsonException.class)
                    .hasMessageContaining("acme:when")
                    .hasMessageContaining("java.time.LocalDate");
        }

        @Test
        void model_invariants_violated_by_a_document_are_reported() {
            // turn index 1 at position 0 violates SearchConversation's invariant
            String json = JSON.write(ModelSamples.conversation()).replace("\"index\":0", "\"index\":5");
            assertThatThrownBy(() -> JSON.readSearchConversation(json)).isInstanceOf(Serp4jJsonException.class);
        }
    }

    @Test
    void response_body_layout() {
        Map<String, StructuredValue> members = JsonTrees.read(JSON.write(ModelSamples.fullResponse())).getMembers();
        assertThat(members.keySet()).containsExactly("formatVersion", "type", "data", "metadata", "analysis");
        StructuredValue feature = members.get("data").path("features").get().get(0).get();
        assertThat(feature.get("kind")).hasValue(StructuredValue.of("generated_answer"));
        assertThat(feature.get("type")).hasValue(StructuredValue.of("ai_overview"));
    }
}
