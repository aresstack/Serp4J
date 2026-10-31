package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.data.MediaInfo;
import com.aresstack.serp4j.core.model.data.SiteLink;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Knowledge panel / infobox: title, subtitle, description with source, facts, images, website and profiles.
 * Type {@link SerpFeatureType#KNOWLEDGE_PANEL}.
 */
public final class KnowledgePanelFeature extends AbstractSerpFeature {

    private final String title;
    private final String subtitle;
    private final String description;
    private final SourceReference descriptionSource;
    private final List<Fact> facts;
    private final List<MediaInfo> images;
    private final String websiteUrl;
    private final List<SiteLink> profiles;

    private KnowledgePanelFeature(Builder builder) {
        super(builder);
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.description = builder.description;
        this.descriptionSource = builder.descriptionSource;
        this.facts = Preconditions.immutableList(builder.facts, "facts");
        this.images = Preconditions.immutableList(builder.images, "images");
        this.websiteUrl = builder.websiteUrl;
        this.profiles = Preconditions.immutableList(builder.profiles, "profiles");
    }

    public static Builder builder(ElementId id, String title) {
        return new Builder(id, title);
    }

    @Override
    public SerpFeatureType getType() {
        return SerpFeatureType.KNOWLEDGE_PANEL;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitKnowledgePanel(this);
    }

    public String getTitle() {
        return title;
    }

    public Optional<String> getSubtitle() {
        return Optional.ofNullable(subtitle);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<SourceReference> getDescriptionSource() {
        return Optional.ofNullable(descriptionSource);
    }

    public List<Fact> getFacts() {
        return facts;
    }

    public List<MediaInfo> getImages() {
        return images;
    }

    public Optional<String> getWebsiteUrl() {
        return Optional.ofNullable(websiteUrl);
    }

    public List<SiteLink> getProfiles() {
        return profiles;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.subtitle = subtitle;
        builder.description = description;
        builder.descriptionSource = descriptionSource;
        builder.facts.addAll(facts);
        builder.images.addAll(images);
        builder.websiteUrl = websiteUrl;
        builder.profiles.addAll(profiles);
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        KnowledgePanelFeature that = (KnowledgePanelFeature) other;
        return title.equals(that.title)
                && Objects.equals(subtitle, that.subtitle)
                && Objects.equals(description, that.description)
                && Objects.equals(descriptionSource, that.descriptionSource)
                && facts.equals(that.facts)
                && images.equals(that.images)
                && Objects.equals(websiteUrl, that.websiteUrl)
                && profiles.equals(that.profiles);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(title, subtitle, description, descriptionSource, facts, images, websiteUrl, profiles);
    }

    /** Builder for {@link KnowledgePanelFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final String title;
        private String subtitle;
        private String description;
        private SourceReference descriptionSource;
        private final List<Fact> facts = new ArrayList<Fact>();
        private final List<MediaInfo> images = new ArrayList<MediaInfo>();
        private String websiteUrl;
        private final List<SiteLink> profiles = new ArrayList<SiteLink>();

        private Builder(ElementId id, String title) {
            super(id);
            this.title = Preconditions.requireText(title, "title");
        }

        private Builder(KnowledgePanelFeature source) {
            super(source);
            this.title = source.title;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = Preconditions.trimToNull(subtitle);
            return this;
        }

        public Builder description(String description) {
            this.description = Preconditions.trimToNull(description);
            return this;
        }

        public Builder descriptionSource(SourceReference source) {
            this.descriptionSource = source;
            return this;
        }

        public Builder fact(Fact fact) {
            facts.add(Preconditions.requireNonNull(fact, "fact"));
            return this;
        }

        public Builder facts(Collection<Fact> facts) {
            for (Fact fact : Preconditions.requireNonNull(facts, "facts")) {
                fact(fact);
            }
            return this;
        }

        public Builder image(MediaInfo image) {
            images.add(Preconditions.requireNonNull(image, "image"));
            return this;
        }

        public Builder images(Collection<MediaInfo> images) {
            for (MediaInfo image : Preconditions.requireNonNull(images, "images")) {
                image(image);
            }
            return this;
        }

        public Builder websiteUrl(String websiteUrl) {
            this.websiteUrl = Preconditions.trimToNull(websiteUrl);
            return this;
        }

        public Builder profile(SiteLink profile) {
            profiles.add(Preconditions.requireNonNull(profile, "profile"));
            return this;
        }

        public Builder profiles(Collection<SiteLink> profiles) {
            for (SiteLink profile : Preconditions.requireNonNull(profiles, "profiles")) {
                profile(profile);
            }
            return this;
        }

        public KnowledgePanelFeature build() {
            return new KnowledgePanelFeature(this);
        }
    }
}
