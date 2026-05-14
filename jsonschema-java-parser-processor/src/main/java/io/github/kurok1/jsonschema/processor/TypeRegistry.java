package io.github.kurok1.jsonschema.processor;

import javax.lang.model.element.TypeElement;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks custom Java types referenced by a schema build so they can be emitted
 * under {@code $defs}. Provides a deterministic pending queue keyed by FQN to
 * make recursive and mutually-referential types terminate.
 */
final class TypeRegistry {

    private static final String DEFS_PREFIX = "#/$defs/";

    private final LinkedHashMap<String, TypeElement> pending = new LinkedHashMap<>();
    private final Set<String> known = new LinkedHashSet<>();

    String register(TypeElement element) {
        String fqn = element.getQualifiedName().toString();
        if (known.add(fqn)) {
            pending.put(fqn, element);
        }
        return fqn;
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    PendingEntry nextPending() {
        Iterator<Map.Entry<String, TypeElement>> it = pending.entrySet().iterator();
        Map.Entry<String, TypeElement> e = it.next();
        it.remove();
        return new PendingEntry(e.getKey(), e.getValue());
    }

    static String refFor(String fqn) {
        return DEFS_PREFIX + fqn;
    }

    static final class PendingEntry {
        private final String fqn;
        private final TypeElement element;

        PendingEntry(String fqn, TypeElement element) {
            this.fqn = fqn;
            this.element = element;
        }

        String fqn() {
            return fqn;
        }

        TypeElement element() {
            return element;
        }
    }
}
