package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable read-only entity view supplied by world queries. */
public final class EntityView {
    private final EntityId id;
    private final EntityState state;
    private final Map<ComponentType<?>, Component> components;

    EntityView(EntityId id, EntityState state, Map<ComponentType<?>, Component> components) {
        this.id = id;
        this.state = state;
        this.components = Collections.unmodifiableMap(new TreeMap<>(components));
    }

    /** Returns the semantic entity ID. */
    public EntityId id() {
        return id;
    }

    /** Returns the lifecycle state represented by this view. */
    public EntityState state() {
        return state;
    }

    /** Returns immutable components sorted by type ID. */
    public Map<ComponentType<?>, Component> components() {
        return components;
    }

    /** Returns one typed component when present. */
    public <T extends Component> Optional<T> component(ComponentType<T> type) {
        Component value = components.get(type);
        return value == null ? Optional.empty() : Optional.of(type.valueClass().cast(value));
    }

    boolean hasAll(ComponentType<?>[] required) {
        for (ComponentType<?> type : required) {
            if (!components.containsKey(type)) {
                return false;
            }
        }
        return true;
    }
}
