package com.aresstack.serp4j.mapping.internal;

import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.feature.AbstractSerpFeature;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GeneratedAnswerFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.ResultGroupFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureVisitor;

/**
 * Copies a feature of one of the core types with a different position and extensions. Returns {@code null} for
 * feature classes outside the core set, which the engine cannot copy.
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class FeatureCopies {

    private FeatureCopies() {
    }

    public static SerpFeature with(SerpFeature feature, final SerpPosition position, final Extensions extensions) {
        return feature.accept(new SerpFeatureVisitor<SerpFeature>() {
            @Override
            public SerpFeature visitKnowledgePanel(KnowledgePanelFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitGeneratedAnswer(GeneratedAnswerFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitFeaturedSnippet(FeaturedSnippetFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitQuestionAnswer(QuestionAnswerFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitRelatedQueries(RelatedQueriesFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitResultGroup(ResultGroupFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitAnswerBox(AnswerBoxFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitGeneric(GenericSerpFeature f) {
                return apply(f.toBuilder()).build();
            }

            @Override
            public SerpFeature visitOther(SerpFeature f) {
                return null;
            }

            private <B extends AbstractSerpFeature.Builder<B>> B apply(B builder) {
                return builder.position(position).extensions(extensions);
            }
        });
    }
}
