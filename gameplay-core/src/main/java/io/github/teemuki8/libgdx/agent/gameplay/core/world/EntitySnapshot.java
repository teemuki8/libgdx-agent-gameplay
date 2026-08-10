package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Deeply immutable completed-tick entity state. */
public record EntitySnapshot(
        EntityId id,
        EntityState state,
        Map<ComponentType<?>, Component> components) implements Comparable<EntitySnapshot> {
    /** Defensively copies and sorts components. */
    public EntitySnapshot {
        components = Collections.unmodifiableMap(new TreeMap<>(components));
    }

    /** Returns one typed component when present. */
    public <T extends Component> Optional<T> component(ComponentType<T> type) {
        Component value = components.get(type);
        return value == null ? Optional.empty() : Optional.of(type.valueClass().cast(value));
    }

    @Override
    public int compareTo(EntitySnapshot other) {
        return id.compareTo(other.id);
    }
}
