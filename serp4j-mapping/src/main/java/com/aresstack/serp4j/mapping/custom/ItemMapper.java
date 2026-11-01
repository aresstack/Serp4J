package com.aresstack.serp4j.mapping.custom;

import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.mapping.MappingContext;
import com.aresstack.serp4j.mapping.SourceElement;

/**
 * Programmable item mapping of one section (ADR-019 §3, level 2). Runs <em>after</em> the declarative fields on
 * the same element and receives the item built from them; it returns the item to use, typically
 * {@code item.toBuilder()…build()} with additional or overriding values (rank semantics, page area, typed
 * extensions). The URL and type always come from the declarative level.
 *
 * <p>Read the element through {@link SourceElement} so that consumed fields do not end up in the unmapped rest.
 * The returned item must keep the element id. If the mapper throws, the engine records a warning and keeps the
 * declarative item. Implementations should be stateless and thread-safe.</p>
 */
@FunctionalInterface
public interface ItemMapper {

    SearchResultItem map(SourceElement element, SearchResultItem item, MappingContext context);
}
