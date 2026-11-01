package com.aresstack.serp4j.mapping.internal;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.mapping.path.PathLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the part of a value no mapping consumed (ADR-019 §4, ADR-006): the value minus every consumed
 * location. Objects and arrays that become empty disappear, and so do JSON {@code null} members, which carry no
 * information. Array elements that are consumed completely are removed; the remaining elements keep their order.
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class Remainder {

    private Remainder() {
    }

    /** The unconsumed rest of {@code value}, or {@code null} if nothing is left. */
    public static StructuredValue of(StructuredValue value, Collection<PathLocation> consumed) {
        Node root = new Node();
        for (PathLocation location : consumed) {
            Node node = root;
            for (Object segment : location.getSegments()) {
                node = node.child(segment);
            }
            node.terminal = true;
        }
        return subtract(value, root);
    }

    private static StructuredValue subtract(StructuredValue value, Node node) {
        if (node.terminal || value.isNull()) {
            return null;
        }
        if (value.isObject()) {
            Map<String, StructuredValue> rest = new LinkedHashMap<String, StructuredValue>();
            for (Map.Entry<String, StructuredValue> e : value.getMembers().entrySet()) {
                Node child = node.children.get(e.getKey());
                StructuredValue r = child == null ? prune(e.getValue()) : subtract(e.getValue(), child);
                if (r != null) {
                    rest.put(e.getKey(), r);
                }
            }
            return rest.isEmpty() ? null : StructuredValue.ofObject(rest);
        }
        if (value.isArray()) {
            List<StructuredValue> rest = new ArrayList<StructuredValue>();
            List<StructuredValue> elements = value.getElements();
            for (int i = 0; i < elements.size(); i++) {
                Node child = node.children.get(Integer.valueOf(i));
                StructuredValue r = child == null ? prune(elements.get(i)) : subtract(elements.get(i), child);
                if (r != null) {
                    rest.add(r);
                }
            }
            return rest.isEmpty() ? null : StructuredValue.ofArray(rest);
        }
        return node.children.isEmpty() ? value : null;
    }

    /** Removes null members and empty containers from a value nothing was consumed from. */
    private static StructuredValue prune(StructuredValue value) {
        return subtract(value, Node.EMPTY);
    }

    private static final class Node {
        static final Node EMPTY = new Node();

        final Map<Object, Node> children = new HashMap<Object, Node>();
        boolean terminal;

        Node child(Object segment) {
            Node child = children.get(segment);
            if (child == null) {
                child = new Node();
                children.put(segment, child);
            }
            return child;
        }
    }
}
