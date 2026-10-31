package com.aresstack.serp4j.core.model.feature;

/**
 * Visitor over the typed SERP features. All typed methods default to {@link #visitOther(SerpFeature)}, so new
 * feature classes never break existing visitors.
 *
 * @param <R> result type
 */
public interface SerpFeatureVisitor<R> {

    default R visitKnowledgePanel(KnowledgePanelFeature feature) {
        return visitOther(feature);
    }

    default R visitGeneratedAnswer(GeneratedAnswerFeature feature) {
        return visitOther(feature);
    }

    default R visitFeaturedSnippet(FeaturedSnippetFeature feature) {
        return visitOther(feature);
    }

    default R visitQuestionAnswer(QuestionAnswerFeature feature) {
        return visitOther(feature);
    }

    default R visitRelatedQueries(RelatedQueriesFeature feature) {
        return visitOther(feature);
    }

    default R visitResultGroup(ResultGroupFeature feature) {
        return visitOther(feature);
    }

    default R visitAnswerBox(AnswerBoxFeature feature) {
        return visitOther(feature);
    }

    default R visitGeneric(GenericSerpFeature feature) {
        return visitOther(feature);
    }

    /** Fallback for every feature without a dedicated method, including {@link GenericSerpFeature}. */
    R visitOther(SerpFeature feature);
}
