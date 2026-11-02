package com.aresstack.serp4j.provider.argos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of one Argos web search, as delivered by the {@code web_search} tool: numbered hits, the search
 * engine hosts that were used (transit, never evidence), one attempt per engine and pending manual challenges.
 */
public final class ArgosSearchOutput {

    private final List<Item> items;
    private final List<String> providerHosts;
    private final List<Attempt> attempts;
    private final List<String> challenges;
    private final String rawText;

    private ArgosSearchOutput(Builder builder) {
        this.items = Collections.unmodifiableList(new ArrayList<Item>(builder.items));
        this.providerHosts = Collections.unmodifiableList(new ArrayList<String>(builder.providerHosts));
        this.attempts = Collections.unmodifiableList(new ArrayList<Attempt>(builder.attempts));
        this.challenges = Collections.unmodifiableList(new ArrayList<String>(builder.challenges));
        this.rawText = builder.rawText;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Item> getItems() {
        return items;
    }

    public List<String> getProviderHosts() {
        return providerHosts;
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }

    public List<String> getChallenges() {
        return challenges;
    }

    /** The tool text exactly as received (for {@code RawPayload}); empty if unknown. */
    public String getRawText() {
        return rawText;
    }

    @Override
    public String toString() {
        return "ArgosSearchOutput{items=" + items.size() + ", providerHosts=" + providerHosts + ", attempts="
                + attempts + ", challenges=" + challenges.size() + '}';
    }

    /** One hit: the number Argos gave it, title, target URL and snippet. */
    public static final class Item {
        private final String id;
        private final String title;
        private final String url;
        private final String snippet;

        public Item(String id, String title, String url, String snippet) {
            this.id = requireText(id, "id");
            this.title = title == null ? "" : title;
            this.url = requireText(url, "url");
            this.snippet = snippet == null ? "" : snippet;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getSnippet() {
            return snippet;
        }

        @Override
        public String toString() {
            return "Item{" + id + ", " + url + '}';
        }
    }

    /** One engine attempt: host and Argos outcome ({@code ORGANIC_RESULTS}, {@code NO_ORGANIC_RESULTS}, ...). */
    public static final class Attempt {
        /** Outcome code of an engine that delivered hits. */
        public static final String ORGANIC_RESULTS = "ORGANIC_RESULTS";

        private final String host;
        private final String outcome;

        public Attempt(String host, String outcome) {
            this.host = requireText(host, "host");
            this.outcome = requireText(outcome, "outcome");
        }

        public String getHost() {
            return host;
        }

        public String getOutcome() {
            return outcome;
        }

        public boolean isOrganicResults() {
            return ORGANIC_RESULTS.equals(outcome);
        }

        @Override
        public String toString() {
            return host + ' ' + outcome;
        }
    }

    public static final class Builder {
        private final List<Item> items = new ArrayList<Item>();
        private final List<String> providerHosts = new ArrayList<String>();
        private final List<Attempt> attempts = new ArrayList<Attempt>();
        private final List<String> challenges = new ArrayList<String>();
        private String rawText = "";

        private Builder() {
        }

        public Builder item(Item item) {
            items.add(requireNonNull(item, "item"));
            return this;
        }

        public Builder providerHost(String host) {
            providerHosts.add(requireText(host, "host"));
            return this;
        }

        public Builder attempt(Attempt attempt) {
            attempts.add(requireNonNull(attempt, "attempt"));
            return this;
        }

        public Builder challenge(String challenge) {
            challenges.add(requireText(challenge, "challenge"));
            return this;
        }

        public Builder rawText(String rawText) {
            this.rawText = rawText == null ? "" : rawText;
            return this;
        }

        public ArgosSearchOutput build() {
            return new ArgosSearchOutput(this);
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (requireNonNull(value, name).trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
