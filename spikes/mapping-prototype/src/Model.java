import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROTOTYP (Spike), kein Produktivcode.
 *
 * Stark vereinfachte Stellvertreter für die Domain-Typen aus WP1/serp4j-core. Sie zeigen nur, WOHIN die
 * Engine schreibt: typisierte Felder, SerpPosition, Extensions (unmapped), GenericSerpFeature, Warnungen.
 */
final class Model {

    private Model() {}

    /** Stellvertreter für SearchResultItem.Builder. */
    static final class Item {
        String id;
        String type = "organic";
        String url;
        String title;
        String snippet;
        final List<String> additionalSnippets = new ArrayList<>();
        String domain;
        String displayUrl;
        Integer rankInGroup;
        Integer rankAbsolute;
        String area = "MAIN";
        /** Extensions: typisierte Keys (namespace:name) und der "unmapped"-Rest als Baum. */
        final Map<String, Object> extensions = new LinkedHashMap<>();
        Map<String, Object> unmapped = new LinkedHashMap<>();

        @Override public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("  Item ").append(id).append(" [").append(type).append("] pos=")
             .append(rankInGroup).append('/').append(rankAbsolute).append(' ').append(area).append('\n');
            b.append("    url=").append(url).append("  title=").append(title).append('\n');
            b.append("    snippet=").append(snippet);
            if (!additionalSnippets.isEmpty()) b.append("  +").append(additionalSnippets);
            b.append('\n');
            if (domain != null || displayUrl != null) b.append("    domain=").append(domain).append("  displayUrl=").append(displayUrl).append('\n');
            if (!extensions.isEmpty()) b.append("    extensions=").append(Json.write(extensions)).append('\n');
            if (!unmapped.isEmpty()) b.append("    unmapped=").append(Json.write(unmapped)).append('\n');
            return b.toString();
        }
    }

    /** Stellvertreter für SerpFeature (typisiert) bzw. GenericSerpFeature (generic=true). */
    static final class Feature {
        String id;
        String type;
        boolean generic;
        final Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, Object> unmapped = new LinkedHashMap<>();

        @Override public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("  Feature ").append(id).append(" [").append(type).append(generic ? ", GENERIC" : "").append("]\n");
            if (!fields.isEmpty()) b.append("    fields=").append(Json.write(fields)).append('\n');
            if (!unmapped.isEmpty()) b.append("    unmapped=").append(Json.write(unmapped)).append('\n');
            return b.toString();
        }
    }

    /** Stellvertreter für das Ergebnis eines Response-Mappers: SearchData + Metadata-Fragmente + Warnungen. */
    static final class Result {
        final List<Item> items = new ArrayList<>();
        final List<Feature> features = new ArrayList<>();
        final Map<String, Object> metadata = new LinkedHashMap<>();
        final List<String> warnings = new ArrayList<>();
        /** Top-Level-Felder der Antwort, die keine Sektion konsumiert hat (→ SearchMetadata.extensions.unmapped). */
        Map<String, Object> unmappedRoot = new LinkedHashMap<>();

        @Override public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("  metadata=").append(Json.write(metadata)).append('\n');
            for (Item it : items) b.append(it);
            for (Feature f : features) b.append(f);
            if (!unmappedRoot.isEmpty()) b.append("  unmappedRoot=").append(Json.write(unmappedRoot)).append('\n');
            for (String w : warnings) b.append("  WARN ").append(w).append('\n');
            return b.toString();
        }
    }
}
