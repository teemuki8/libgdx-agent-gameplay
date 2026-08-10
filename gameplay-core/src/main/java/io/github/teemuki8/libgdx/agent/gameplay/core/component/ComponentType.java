package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Objects;

/** Explicit stable component identity paired with its compile-time value type. */
public record ComponentType<T extends Component>(String id, Class<T> valueClass)
        implements Comparable<ComponentType<?>> {
    /** Validates the stable ID and value class. */
    public ComponentType {
        id = IdentifierRules.requireIdentifier(id, "componentTypeId");
        Objects.requireNonNull(valueClass, "valueClass");
    }

    @Override
    public int compareTo(ComponentType<?> other) {
        return id.compareTo(other.id);
    }
}
