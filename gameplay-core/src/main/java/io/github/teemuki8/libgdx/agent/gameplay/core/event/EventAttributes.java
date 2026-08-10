package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable key-sorted bounded gameplay-event attributes. */
public record EventAttributes(Map<String, EventAttributeValue> values) {
    private static final int MAX_ATTRIBUTES = 32;
    private static final EventAttributes EMPTY = new EventAttributes(Map.of());

    /** Validates and copies all keys and values. */
    public EventAttributes {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_ATTRIBUTES) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.EVENT_LIMIT_EXCEEDED,
                    "create-event-attributes",
                    "at most 32 attributes",
                    Integer.toString(values.size()),
                    "Keep only bounded evidence required to explain the event.");
        }
        TreeMap<String, EventAttributeValue> copy = new TreeMap<>();
        values.forEach((key, value) -> copy.put(
                IdentifierRules.requireIdentifier(key, "eventAttribute"),
                Objects.requireNonNull(value, "eventAttributeValue")));
        values = Collections.unmodifiableMap(copy);
    }

    /** Creates attributes from a map. */
    public static EventAttributes of(Map<String, EventAttributeValue> values) {
        return new EventAttributes(values);
    }

    /** Returns the shared empty attributes. */
    public static EventAttributes empty() {
        return EMPTY;
    }
}
