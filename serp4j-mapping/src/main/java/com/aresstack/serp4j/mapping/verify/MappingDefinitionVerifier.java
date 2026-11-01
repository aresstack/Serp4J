package com.aresstack.serp4j.mapping.verify;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.mapping.ElementType;
import com.aresstack.serp4j.mapping.FieldMapping;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.mapping.SectionDefinition;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a mapping definition against real provider fixtures (ADR-019 §5, architecture.md §5.6 rule 5). JUnit-free
 * building blocks for {@code MappingDefinitionContractTest} in {@code serp4j-testkit}:
 *
 * <ul>
 *   <li>{@link #coverage}: every section path, every source path and every discriminator value of the definition
 *       must match in at least one fixture – dead mappings show up instead of silently mapping nothing;</li>
 *   <li>{@link #verifyUnknownTypeSurvives}: an element of an unknown type injected into every polymorphic section
 *       must come out as {@link GenericSerpFeature} with its full content and an
 *       {@link SearchWarning#UNKNOWN_ELEMENT} warning;</li>
 *   <li>golden output per fixture: {@link MappingSnapshot#render}.</li>
 * </ul>
 */
public final class MappingDefinitionVerifier {

    /** Discriminator value of the probe element injected by {@link #verifyUnknownTypeSurvives}. */
    public static final String UNKNOWN_TYPE_PROBE = "serp4j_unknown_type_probe";

    private MappingDefinitionVerifier() {
    }

    // ================================================================== coverage

    /**
     * Determines which parts of {@code definition} match in none of the {@code fixtures}. Section fields are
     * checked only against elements of the types they apply to; fields that only have a default are ignored.
     */
    public static CoverageReport coverage(MappingDefinition definition, Collection<StructuredValue> fixtures) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(fixtures, "fixtures");
        Map<String, String> unmatched = new LinkedHashMap<String, String>();
        Set<String> matched = new LinkedHashSet<String>();

        for (FieldMapping f : definition.getMetadata()) {
            for (TreePath source : f.getSources()) {
                String key = "metadata." + f.getTarget() + " <- " + source;
                unmatched.put(key, "metadata field '" + f.getTarget() + "': source '" + source + "' matches in no fixture");
            }
        }
        for (SectionDefinition s : definition.getSections()) {
            unmatched.put(sectionKey(s), "section '" + s.getId() + "': path '" + s.getPath() + "' matches in no fixture");
            for (String value : s.getDiscriminatorMap().keySet()) {
                unmatched.put(discKey(s, value), "section '" + s.getId() + "': discriminator value '" + value
                        + "' occurs in no fixture");
            }
            for (ElementType type : s.getElementTypes()) {
                for (FieldMapping f : s.fieldsFor(type)) {
                    for (TreePath source : f.getSources()) {
                        unmatched.put(fieldKey(s, type, f, source), "section '" + s.getId() + "': field '" + f.getTarget()
                                + "' source '" + source + "' matches in no fixture"
                                + (isTypeSpecific(s, type, f) ? " (type " + type.getCode() + ")" : ""));
                    }
                }
            }
        }

        for (StructuredValue fixture : fixtures) {
            Objects.requireNonNull(fixture, "fixture");
            for (FieldMapping f : definition.getMetadata()) {
                for (TreePath source : f.getSources()) {
                    if (hasValue(source, fixture)) {
                        matched.add("metadata." + f.getTarget() + " <- " + source);
                    }
                }
            }
            for (SectionDefinition s : definition.getSections()) {
                for (StructuredValue element : s.getPath().select(fixture)) {
                    if (element.isNull()) {
                        continue;
                    }
                    matched.add(sectionKey(s));
                    ElementType type = s.getDefaultType().orElse(null);
                    if (s.getDiscriminator().isPresent()) {
                        String value = text(s.getDiscriminator().get(), element);
                        if (value != null) {
                            if (s.getSkipWhen().contains(value)) {
                                continue;
                            }
                            type = s.getDiscriminatorMap().get(value);
                            if (type != null) {
                                matched.add(discKey(s, value));
                            }
                        }
                    }
                    if (type == null) {
                        continue;
                    }
                    for (FieldMapping f : s.fieldsFor(type)) {
                        for (TreePath source : f.getSources()) {
                            if (hasValue(source, element)) {
                                matched.add(fieldKey(s, type, f, source));
                            }
                        }
                    }
                }
            }
        }
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, String> e : unmatched.entrySet()) {
            if (!matched.contains(e.getKey())) {
                problems.add(e.getValue());
            }
        }
        return new CoverageReport(definition, fixtures.size(), problems);
    }

    private static boolean isTypeSpecific(SectionDefinition s, ElementType type, FieldMapping f) {
        List<FieldMapping> specific = s.getFieldsByType().get(type.getCode());
        return specific != null && specific.contains(f);
    }

    private static String sectionKey(SectionDefinition s) {
        return "section." + s.getId();
    }

    private static String discKey(SectionDefinition s, String value) {
        return "section." + s.getId() + ".discriminator=" + value;
    }

    /** Type-specific fields are tracked per type, so a dead path for one type is not hidden by another type. */
    private static String fieldKey(SectionDefinition s, ElementType type, FieldMapping f, TreePath source) {
        String scope = isTypeSpecific(s, type, f) ? ".type." + type.getCode() : "";
        return "section." + s.getId() + scope + ".field." + f.getTarget() + " <- " + source;
    }

    private static boolean hasValue(TreePath path, StructuredValue value) {
        for (StructuredValue v : path.select(value)) {
            if (!v.isNull()) {
                return true;
            }
        }
        return false;
    }

    private static String text(TreePath path, StructuredValue value) {
        for (StructuredValue v : path.select(value)) {
            if (v.isString()) {
                return v.asText().get();
            }
            if (v.isNumber()) {
                return v.asNumber().get().toPlainString();
            }
            if (v.isBoolean()) {
                return v.asBoolean().get().toString();
            }
        }
        return null;
    }

    // ================================================================== unknown type survives

    /**
     * Injects a probe element with the discriminator value {@value #UNKNOWN_TYPE_PROBE} into the element list of
     * every section that has a discriminator (and a path ending in {@code [*]}, with a discriminator path made of
     * names only), maps the modified fixture with {@code engine} and checks that each probe survives as
     * {@link GenericSerpFeature} with unchanged content plus an {@link SearchWarning#UNKNOWN_ELEMENT} warning.
     *
     * @return the problems found; empty if every probe survived. Sections that cannot be probed are reported in
     *         {@link UnknownTypeReport#getSkippedSections()}.
     */
    public static UnknownTypeReport verifyUnknownTypeSurvives(MappingEngine engine, StructuredValue fixture) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(fixture, "fixture");
        List<String> problems = new ArrayList<String>();
        List<String> probed = new ArrayList<String>();
        List<String> skipped = new ArrayList<String>();
        for (SectionDefinition s : engine.getDefinition().getSections()) {
            if (!s.getDiscriminator().isPresent()) {
                continue;
            }
            String pathText = s.getPath().toString();
            List<String> discNames = names(s.getDiscriminator().get().toString());
            if (!pathText.endsWith("[*]") || discNames == null) {
                skipped.add(s.getId());
                continue;
            }
            TreePath container = TreePath.parse(pathText.substring(0, pathText.length() - 3));
            List<TreePath.Match> lists = container.locate(fixture);
            TreePath.Match target = null;
            for (TreePath.Match m : lists) {
                if (m.getValue().isArray()) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                skipped.add(s.getId());
                continue;
            }
            StructuredValue probe = probe(discNames, s.getId());
            List<StructuredValue> extended = new ArrayList<StructuredValue>(target.getValue().getElements());
            extended.add(probe);
            StructuredValue modified = replace(fixture, target.getLocation().getSegments(), 0,
                    StructuredValue.ofArray(extended));
            probed.add(s.getId());

            MappingResult result = engine.map(modified);
            GenericSerpFeature found = null;
            for (SerpFeature f : result.getData().getFeatures()) {
                if (f instanceof GenericSerpFeature && ((GenericSerpFeature) f).getContent().equals(probe)) {
                    found = (GenericSerpFeature) f;
                }
            }
            if (found == null) {
                problems.add("section '" + s.getId() + "': element of unknown type was not kept as GenericSerpFeature");
            } else if (!found.getType().getCode().equals(UNKNOWN_TYPE_PROBE)) {
                problems.add("section '" + s.getId() + "': unknown element kept with type '" + found.getType().getCode()
                        + "' instead of '" + UNKNOWN_TYPE_PROBE + "'");
            }
            boolean warned = false;
            for (SearchWarning w : result.getWarnings()) {
                if (SearchWarning.UNKNOWN_ELEMENT.equals(w.getCode()) && w.getMessage().contains(UNKNOWN_TYPE_PROBE)) {
                    warned = true;
                }
            }
            if (!warned) {
                problems.add("section '" + s.getId() + "': no " + SearchWarning.UNKNOWN_ELEMENT + " warning for the unknown element");
            }
        }
        return new UnknownTypeReport(probed, skipped, problems);
    }

    private static List<String> names(String path) {
        String p = path.startsWith("$.") ? path.substring(2) : path;
        List<String> out = new ArrayList<String>();
        for (String part : p.split("\\.", -1)) {
            if (part.isEmpty() || part.indexOf('[') >= 0 || part.indexOf('\'') >= 0 || part.equals("$")) {
                return null;
            }
            out.add(part);
        }
        return out;
    }

    private static StructuredValue probe(List<String> discNames, String sectionId) {
        StructuredValue value = StructuredValue.of(UNKNOWN_TYPE_PROBE);
        for (int i = discNames.size() - 1; i >= 0; i--) {
            StructuredValue.ObjectBuilder b = StructuredValue.objectBuilder().put(discNames.get(i), value);
            if (i == 0) {
                b.put("serp4j_probe_section", sectionId).put("serp4j_probe_payload",
                        StructuredValue.objectBuilder().put("nested", true).build());
            }
            value = b.build();
        }
        return value;
    }

    private static StructuredValue replace(StructuredValue tree, List<Object> segments, int index,
                                           StructuredValue replacement) {
        if (index == segments.size()) {
            return replacement;
        }
        Object segment = segments.get(index);
        if (segment instanceof Integer) {
            List<StructuredValue> elements = new ArrayList<StructuredValue>(tree.getElements());
            int i = (Integer) segment;
            elements.set(i, replace(elements.get(i), segments, index + 1, replacement));
            return StructuredValue.ofArray(elements);
        }
        Map<String, StructuredValue> members = new LinkedHashMap<String, StructuredValue>(tree.getMembers());
        String name = (String) segment;
        members.put(name, replace(members.get(name), segments, index + 1, replacement));
        return StructuredValue.ofObject(members);
    }

    // ================================================================== reports

    /** Result of {@link #coverage}. Immutable. */
    public static final class CoverageReport {
        private final String definition;
        private final int fixtureCount;
        private final List<String> unmatched;

        CoverageReport(MappingDefinition definition, int fixtureCount, List<String> unmatched) {
            this.definition = definition.toString();
            this.fixtureCount = fixtureCount;
            this.unmatched = Collections.unmodifiableList(new ArrayList<String>(unmatched));
        }

        /** {@code true} if every path and discriminator value matched in at least one fixture. */
        public boolean isComplete() {
            return unmatched.isEmpty();
        }

        /** One line per part of the definition that matched in no fixture. */
        public List<String> getUnmatched() {
            return unmatched;
        }

        public int getFixtureCount() {
            return fixtureCount;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("coverage of ").append(definition).append(" over ")
                    .append(fixtureCount).append(" fixture(s): ");
            if (unmatched.isEmpty()) {
                return out.append("complete").toString();
            }
            out.append(unmatched.size()).append(" unmatched");
            for (String u : unmatched) {
                out.append("\n  - ").append(u);
            }
            return out.toString();
        }
    }

    /** Result of {@link #verifyUnknownTypeSurvives}. Immutable. */
    public static final class UnknownTypeReport {
        private final List<String> probedSections;
        private final List<String> skippedSections;
        private final List<String> problems;

        UnknownTypeReport(List<String> probed, List<String> skipped, List<String> problems) {
            this.probedSections = Collections.unmodifiableList(new ArrayList<String>(probed));
            this.skippedSections = Collections.unmodifiableList(new ArrayList<String>(skipped));
            this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
        }

        public boolean isOk() {
            return problems.isEmpty();
        }

        public List<String> getProblems() {
            return problems;
        }

        /** Sections a probe was injected into. */
        public List<String> getProbedSections() {
            return probedSections;
        }

        /** Sections with a discriminator that could not be probed (path without {@code [*]}, no list in the fixture). */
        public List<String> getSkippedSections() {
            return skippedSections;
        }

        @Override
        public String toString() {
            return "unknown-type check: probed " + probedSections + ", skipped " + skippedSections
                    + (problems.isEmpty() ? ", ok" : ", problems " + problems);
        }
    }
}
