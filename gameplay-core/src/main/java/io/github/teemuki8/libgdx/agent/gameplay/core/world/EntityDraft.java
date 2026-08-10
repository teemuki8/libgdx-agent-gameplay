package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Detached immutable entity definition queued for a future activation barrier. */
public final class EntityDraft {
    private final EntityId id;
    private final Map<ComponentType<?>, Component> components;

    private EntityDraft(EntityId id, Map<ComponentType<?>, Component> components) {
        this.id = id;
        this.components = immutableComponents(components);
    }

    /** Returns a draft builder for one semantic entity ID. */
    public static Builder builder(EntityId id) {
        return new Builder(id);
    }

    /** Returns the semantic entity ID. */
    public EntityId id() {
        return id;
    }

    /** Returns immutable components sorted by stable type ID. */
    public Map<ComponentType<?>, Component> components() {
        return components;
    }

    private static Map<ComponentType<?>, Component> immutableComponents(
            Map<ComponentType<?>, Component> source) {
        TreeMap<ComponentType<?>, Component> sorted = new TreeMap<>();
        source.forEach((type, component) -> sorted.put(
                Objects.requireNonNull(type, "componentType"),
                Objects.requireNonNull(component, "component")));
        return Collections.unmodifiableMap(sorted);
    }

    /** Mutable single-use draft builder. */
    public static final class Builder {
        private final EntityId id;
        private final Map<ComponentType<?>, Component> components = new TreeMap<>();

        private Builder(EntityId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Adds one component, rejecting duplicate types and type mismatches. */
        public <T extends Component> Builder with(ComponentType<T> type, T component) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(component, "component");
            if (!type.valueClass().isInstance(component)) {
                throw failure(GameplayDiagnosticCode.COMPONENT_TYPE_MISMATCH,
                        type.valueClass().getName(), component.getClass().getName(),
                        "Pair the component value with its declared ComponentType.");
            }
            if (components.containsKey(type)) {
                throw failure(GameplayDiagnosticCode.DUPLICATE_COMPONENT_TYPE,
                        "one value for component " + type.id(), type.id(),
                        "Replace the draft value before building instead of adding it twice.");
            }
            components.put(type, component);
            return this;
        }

        /** Builds an immutable detached draft. */
        public EntityDraft build() {
            return new EntityDraft(id, components);
        }

        private static GameplayException failure(
                GameplayDiagnosticCode code,
                String expected,
                String observed,
                String correction) {
            return GameplayException.validation(code, "build-entity-draft",
                    expected, observed, correction);
        }
    }
}
