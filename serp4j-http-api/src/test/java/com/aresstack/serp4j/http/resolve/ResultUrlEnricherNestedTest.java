package com.aresstack.serp4j.http.resolve;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SiteLink;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GeneratedAnswerFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nested URLs (site links and feature sources) are resolved in the same run as result URLs: one request per distinct
 * URL, the shared limit of four, a trace on the owning element, failures kept unchanged. Synthetic model objects; the
 * recorded provider answers are covered by the DataForSEO and SerpApi tests.
 */
class ResultUrlEnricherNestedTest {

    private static final String GOTO = "https://www.google.com/goto?url=";

    /** Resolves {@code GOTO + x} to {@code https://real.example/x}, fails for tokens starting with "bad". */
    private static final class FakeResolver implements UrlResolver {
        final List<String> calls = new CopyOnWriteArrayList<String>();
        final List<CompletableFuture<String>> held = new CopyOnWriteArrayList<CompletableFuture<String>>();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        volatile boolean hold;

        @Override
        public boolean supports(String url) {
            return url.startsWith(GOTO);
        }

        @Override
        public CompletableFuture<String> resolve(String url, Duration timeout) {
            calls.add(url);
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            CompletableFuture<String> f = new CompletableFuture<String>();
            if (hold) {
                held.add(f);
                return f;
            }
            complete(url, f);
            return f;
        }

        void complete(String url, CompletableFuture<String> f) {
            running.decrementAndGet();
            String token = url.substring(GOTO.length());
            if (token.startsWith("bad")) {
                f.completeExceptionally(new UrlResolutionException(UrlResolutionException.NOT_A_REDIRECT, "403"));
            } else {
                f.complete("https://real.example/" + token);
            }
        }

        void releaseAll() {
            while (!held.isEmpty()) {
                CompletableFuture<String> f = held.remove(0);
                complete(GOTO + "x", f);
            }
        }
    }

    private final FakeResolver resolver = new FakeResolver();
    private final ResultUrlEnricher enricher =
            new ResultUrlEnricher(ProviderId.of("test"), resolver, Duration.ofSeconds(3), Clock.systemUTC());

    private static SearchResponse response() {
        SearchResultItem item = SearchResultItem.builder(ElementId.of("r1"), ResultType.ORGANIC, GOTO + "top")
                .siteLink(SiteLink.of(GOTO + "link1", "Link 1"))
                .siteLink(SiteLink.of("https://direct.example/link2", "Link 2"))
                .siteLink(SiteLink.of(GOTO + "bad-link3", "Link 3"))
                .build();
        AnswerBoxFeature answerBox = AnswerBoxFeature.builder(ElementId.of("f1"), SerpFeatureType.ANSWER_BOX, "42")
                .source(SourceReference.builder(GOTO + "answer").title("Answer").domain("www.google.com").build())
                .build();
        FeaturedSnippetFeature snippet = FeaturedSnippetFeature.builder(ElementId.of("f2"), "snippet",
                SourceReference.of(GOTO + "top", "Same as result")).build();
        QuestionAnswerFeature paa = QuestionAnswerFeature.builder(ElementId.of("f3"), SerpFeatureType.PEOPLE_ALSO_ASK)
                .question(QuestionAnswer.of("Without source?"))
                .question(QuestionAnswer.of("With source?", "Yes", SourceReference.of(GOTO + "paa")))
                .build();
        KnowledgePanelFeature panel = KnowledgePanelFeature.builder(ElementId.of("f4"), "Panel")
                .websiteUrl(GOTO + "website")
                .profile(SiteLink.of(GOTO + "profile", "Profile"))
                .fact(Fact.builder("Founder").value("Someone", GOTO + "fact").value("Plain").build())
                .build();
        GeneratedAnswerFeature ai = GeneratedAnswerFeature.builder(ElementId.of("f5"), SerpFeatureType.AI_OVERVIEW,
                "text").reference(SourceReference.of("https://direct.example/ref"))
                .reference(SourceReference.of(GOTO + "ref")).build();
        RelatedQueriesFeature related = RelatedQueriesFeature.builder(ElementId.of("f6")).query("more").build();
        return SearchResponse.builder(SearchData.builder().result(item).feature(answerBox).feature(snippet)
                .feature(paa).feature(panel).feature(ai).feature(related).build()).build();
    }

    private SearchResponse enrich(SearchResponse response) throws Exception {
        return enricher.enrich(response, now -> Optional.empty()).get(5, TimeUnit.SECONDS);
    }

    private static String text(StructuredValue entry, String name) {
        return entry.get(name).flatMap(StructuredValue::asText).orElse(null);
    }

    @Test
    void nestedUrlsAreResolvedOncePerDistinctUrlAndTracedOnTheirOwner() throws Exception {
        SearchResponse in = response();
        SearchResponse out = enrich(in);

        // top, link1, bad-link3, answer, paa, website, profile, fact, ref – "top" again in the snippet is deduplicated
        assertThat(resolver.calls).hasSize(9).doesNotHaveDuplicates();

        SearchResultItem item = out.getData().getResults().get(0);
        assertThat(item.getUrl()).isEqualTo("https://real.example/top");
        assertThat(item.getExtensions().get(ResultUrlEnricher.ORIGINAL_URL)).hasValue(GOTO + "top");
        assertThat(item.getSiteLinks()).extracting(SiteLink::getUrl).containsExactly("https://real.example/link1",
                "https://direct.example/link2", GOTO + "bad-link3");
        assertThat(item.getSiteLinks().get(0).getTitle()).hasValue("Link 1");
        List<StructuredValue> trace = item.getExtensions().get(ResultUrlEnricher.NESTED_URL_RESOLUTIONS).get()
                .getElements();
        assertThat(trace).hasSize(2);
        assertThat(text(trace.get(0), "field")).isEqualTo("siteLinks[0]");
        assertThat(text(trace.get(0), "original_url")).isEqualTo(GOTO + "link1");
        assertThat(text(trace.get(0), "url_resolution")).isEqualTo(ResultUrlEnricher.RESOLVED);
        assertThat(text(trace.get(0), "url")).isEqualTo("https://real.example/link1");
        assertThat(text(trace.get(1), "field")).isEqualTo("siteLinks[2]");
        assertThat(text(trace.get(1), "url_resolution")).isEqualTo(UrlResolutionException.NOT_A_REDIRECT);
        assertThat(text(trace.get(1), "url")).isNull();

        AnswerBoxFeature answerBox = out.getData().getFeatures(AnswerBoxFeature.class).get(0);
        assertThat(answerBox.getSource().get().getUrl()).isEqualTo("https://real.example/answer");
        assertThat(answerBox.getSource().get().getDomain()).hasValue("real.example");
        assertThat(answerBox.getSource().get().getTitle()).hasValue("Answer");
        assertThat(text(answerBox.getExtensions().get(ResultUrlEnricher.NESTED_URL_RESOLUTIONS).get().get(0).get(),
                "field")).isEqualTo("source");

        FeaturedSnippetFeature snippet = out.getData().getFeatures(FeaturedSnippetFeature.class).get(0);
        assertThat(snippet.getSource().getUrl()).isEqualTo("https://real.example/top");
        assertThat(snippet.getId()).isEqualTo(ElementId.of("f2"));

        QuestionAnswerFeature paa = out.getData().getFeatures(QuestionAnswerFeature.class).get(0);
        assertThat(paa.getQuestions().get(0).getSource()).isEmpty();
        assertThat(paa.getQuestions().get(1).getSource().get().getUrl()).isEqualTo("https://real.example/paa");
        assertThat(paa.getQuestions().get(1).getAnswer()).hasValue("Yes");
        assertThat(text(paa.getExtensions().get(ResultUrlEnricher.NESTED_URL_RESOLUTIONS).get().get(0).get(),
                "field")).isEqualTo("questions[1].source");

        KnowledgePanelFeature panel = out.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(panel.getWebsiteUrl()).hasValue("https://real.example/website");
        assertThat(panel.getProfiles().get(0).getUrl()).isEqualTo("https://real.example/profile");
        assertThat(panel.getFacts().get(0).getValues().get(0).getUrl()).hasValue("https://real.example/fact");
        assertThat(panel.getFacts().get(0).getValues().get(1).getText()).isEqualTo("Plain");
        assertThat(panel.getExtensions().get(ResultUrlEnricher.NESTED_URL_RESOLUTIONS).get().getElements())
                .extracting(e -> text(e, "field")).containsExactly("websiteUrl", "profiles[0]", "facts[0].values[0]");

        GeneratedAnswerFeature ai = out.getData().getFeatures(GeneratedAnswerFeature.class).get(0);
        assertThat(ai.getReferences()).extracting(SourceReference::getUrl)
                .containsExactly("https://direct.example/ref", "https://real.example/ref");

        assertThat(out.getData().getFeatures().get(5)).as("features without URLs stay identical")
                .isSameAs(in.getData().getFeatures().get(5));
    }

    @Test
    void theConcurrencyLimitCoversResultAndNestedUrlsTogether() throws Exception {
        resolver.hold = true;
        CompletableFuture<SearchResponse> f = enricher.enrich(response(), now -> Optional.empty());
        assertThat(resolver.calls).hasSize(ResultUrlEnricher.MAX_CONCURRENT);
        while (!f.isDone()) {
            resolver.releaseAll();
        }
        assertThat(resolver.maxRunning.get()).isEqualTo(ResultUrlEnricher.MAX_CONCURRENT);
        assertThat(resolver.calls).hasSize(9);
    }

    @Test
    void cancellationStopsNestedResolutionsToo() {
        resolver.hold = true;
        CompletableFuture<SearchResponse> f = enricher.enrich(response(), now -> Optional.empty());
        f.cancel(true);
        assertThat(resolver.held).allMatch(CompletableFuture::isCancelled);
        assertThat(resolver.calls).hasSize(ResultUrlEnricher.MAX_CONCURRENT);
    }

    @Test
    void responsesWithoutRedirectsStayIdentical() throws Exception {
        SearchResponse plain = SearchResponse.builder(SearchData.builder()
                .result(SearchResultItem.builder(ElementId.of("r"), ResultType.ORGANIC, "https://direct.example/")
                        .siteLink(SiteLink.of("https://direct.example/a")).build()).build()).build();
        assertThat(enrich(plain)).isSameAs(plain);
        assertThat(resolver.calls).isEmpty();
    }
}
