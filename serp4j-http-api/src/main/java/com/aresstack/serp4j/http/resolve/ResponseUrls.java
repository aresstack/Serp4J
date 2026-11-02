package com.aresstack.serp4j.http.resolve;

import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SiteLink;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GeneratedAnswerFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SourceReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Knows every URL of the domain model that may point to a result page – the top-level result URL and the nested
 * ones: site links of a result, the sources of answer boxes, featured snippets, generated answers and question
 * answers (people also ask), and the website, description source, profiles and fact links of a knowledge panel.
 * Images, thumbnails and related-query URLs are no result targets and are left alone; generic features keep their
 * provider content untouched (it is raw provider data, not a domain URL).
 *
 * <p>{@link #collect} lists the URLs, {@link #rewrite} replaces them through a {@link Mapping}. Internal helper of
 * {@link ResultUrlEnricher}.</p>
 */
final class ResponseUrls {

    /** Replacement of one URL; {@code null} = keep it. Records what it did per owner. */
    interface Mapping {
        /** @return the replacement URL or {@code null}; {@code field} names the nested location for the record */
        String map(String url, String field, Owner owner);
    }

    /** Collects the trace entries of one owning element (result or feature). */
    static final class Owner {
        final List<StructuredValue> entries = new ArrayList<StructuredValue>();

        void add(String field, String originalUrl, String status, String resolvedUrl) {
            StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder()
                    .put("field", field)
                    .put("original_url", originalUrl)
                    .put("url_resolution", status);
            if (resolvedUrl != null) {
                b.put("url", resolvedUrl);
            }
            entries.add(b.build());
        }

        Extensions applyTo(Extensions extensions) {
            return entries.isEmpty() ? extensions
                    : extensions.with(ResultUrlEnricher.NESTED_URL_RESOLUTIONS, StructuredValue.ofArray(entries));
        }
    }

    private ResponseUrls() {
    }

    /** Adds every nested URL of {@code item} (not the item URL itself) to {@code out}. */
    static void collect(SearchResultItem item, List<String> out) {
        for (SiteLink link : item.getSiteLinks()) {
            out.add(link.getUrl());
        }
    }

    /** Adds every URL of {@code feature} to {@code out}. */
    static void collect(SerpFeature feature, final List<String> out) {
        rewrite(feature, new Mapping() {
            @Override
            public String map(String url, String field, Owner owner) {
                out.add(url);
                return null;
            }
        });
    }

    /** {@code item} with its site links rewritten; the item URL is handled by the caller. */
    static SearchResultItem rewriteNested(SearchResultItem item, Mapping mapping) {
        if (item.getSiteLinks().isEmpty()) {
            return item;
        }
        Owner owner = new Owner();
        List<SiteLink> links = new ArrayList<SiteLink>();
        boolean changed = false;
        int i = 0;
        for (SiteLink link : item.getSiteLinks()) {
            String target = mapping.map(link.getUrl(), "siteLinks[" + i++ + "]", owner);
            links.add(target != null ? link.withUrl(target) : link);
            changed |= target != null;
        }
        if (owner.entries.isEmpty()) {
            return item;
        }
        SearchResultItem.Builder b = item.toBuilder();
        if (changed) {
            b.clearSiteLinks().siteLinks(links);
        }
        return b.extensions(owner.applyTo(item.getExtensions())).build();
    }

    /** {@code feature} with all its URLs rewritten, or {@code feature} itself if nothing was touched. */
    static SerpFeature rewrite(SerpFeature feature, Mapping mapping) {
        Owner owner = new Owner();
        SerpFeature out = feature;
        if (feature instanceof AnswerBoxFeature) {
            AnswerBoxFeature f = (AnswerBoxFeature) feature;
            if (f.getSource().isPresent()) {
                SourceReference s = source(f.getSource().get(), "source", mapping, owner);
                out = f.toBuilder().source(s).extensions(owner.applyTo(f.getExtensions())).build();
            }
        } else if (feature instanceof FeaturedSnippetFeature) {
            FeaturedSnippetFeature f = (FeaturedSnippetFeature) feature;
            SourceReference s = source(f.getSource(), "source", mapping, owner);
            FeaturedSnippetFeature.Builder b = FeaturedSnippetFeature.builder(f.getId(), f.getText(), s)
                    .provenance(f.getProvenance()).extensions(owner.applyTo(f.getExtensions()));
            if (f.getPosition().isPresent()) {
                b.position(f.getPosition().get());
            }
            out = b.build();
        } else if (feature instanceof GeneratedAnswerFeature) {
            GeneratedAnswerFeature f = (GeneratedAnswerFeature) feature;
            List<SourceReference> refs = new ArrayList<SourceReference>();
            int i = 0;
            for (SourceReference r : f.getReferences()) {
                refs.add(source(r, "references[" + i++ + "]", mapping, owner));
            }
            out = f.toBuilder().clearReferences().references(refs)
                    .extensions(owner.applyTo(f.getExtensions())).build();
        } else if (feature instanceof QuestionAnswerFeature) {
            QuestionAnswerFeature f = (QuestionAnswerFeature) feature;
            List<QuestionAnswer> questions = new ArrayList<QuestionAnswer>();
            int i = 0;
            for (QuestionAnswer q : f.getQuestions()) {
                if (q.getSource().isPresent()) {
                    SourceReference s = source(q.getSource().get(), "questions[" + i + "].source", mapping, owner);
                    q = QuestionAnswer.of(q.getQuestion(), q.getAnswer().orElse(null), s);
                }
                questions.add(q);
                i++;
            }
            out = f.toBuilder().clearQuestions().questions(questions)
                    .extensions(owner.applyTo(f.getExtensions())).build();
        } else if (feature instanceof KnowledgePanelFeature) {
            out = knowledgePanel((KnowledgePanelFeature) feature, mapping, owner);
        }
        return owner.entries.isEmpty() ? feature : out;
    }

    private static SerpFeature knowledgePanel(KnowledgePanelFeature f, Mapping mapping, Owner owner) {
        KnowledgePanelFeature.Builder b = f.toBuilder();
        if (f.getWebsiteUrl().isPresent()) {
            String target = mapping.map(f.getWebsiteUrl().get(), "websiteUrl", owner);
            if (target != null) {
                b.websiteUrl(target);
            }
        }
        if (f.getDescriptionSource().isPresent()) {
            b.descriptionSource(source(f.getDescriptionSource().get(), "descriptionSource", mapping, owner));
        }
        List<SiteLink> profiles = new ArrayList<SiteLink>();
        int i = 0;
        for (SiteLink p : f.getProfiles()) {
            String target = mapping.map(p.getUrl(), "profiles[" + i++ + "]", owner);
            profiles.add(target != null ? p.withUrl(target) : p);
        }
        List<Fact> facts = new ArrayList<Fact>();
        i = 0;
        for (Fact fact : f.getFacts()) {
            Fact.Builder fb = Fact.builder(fact.getKey());
            int j = 0;
            for (Fact.Value v : fact.getValues()) {
                String target = v.getUrl().isPresent()
                        ? mapping.map(v.getUrl().get(), "facts[" + i + "].values[" + j + "]", owner) : null;
                fb.value(target != null ? Fact.Value.of(v.getText(), target) : v);
                j++;
            }
            facts.add(fb.build());
            i++;
        }
        return b.clearProfiles().profiles(profiles).clearFacts().facts(facts)
                .extensions(owner.applyTo(f.getExtensions())).build();
    }

    private static SourceReference source(SourceReference s, String field, Mapping mapping, Owner owner) {
        String target = mapping.map(s.getUrl(), field, owner);
        if (target == null) {
            return s;
        }
        return s.withUrl(target, ResultUrlEnricher.host(target).orElse(null));
    }
}
