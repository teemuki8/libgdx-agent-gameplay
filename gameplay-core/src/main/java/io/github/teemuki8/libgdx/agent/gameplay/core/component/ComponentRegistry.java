package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable explicit registry of component types and their stable IDs. */
public final class ComponentRegistry {
    private static final int MAX_TYPES = 256;
    private final Map<String, ComponentType<?>> byId;

    private ComponentRegistry(Map<String, ComponentType<?>> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    /** Returns an empty registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the type with the given stable ID or fails closed. */
    public ComponentType<?> require(String id) {
        String validated = IdentifierRules.requireIdentifier(id, "componentTypeId");
        ComponentType<?> type = byId.get(validated);
        if (type == null) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNKNOWN_COMPONENT_TYPE,
                    "resolve-component-type",
                    "one of " + byId.keySet(),
                    validated,
                    "Register the component type before parsing or spawning it.");
        }
        return type;
    }

    /** Returns registered types in deterministic insertion order. */
    public List<ComponentType<?>> types() {
        return List.copyOf(byId.values());
    }

    /** Mutable single-use builder for an immutable registry. */
    public static final class Builder {
        private final Map<String, ComponentType<?>> byId = new LinkedHashMap<>();
        private final Map<Class<?>, ComponentType<?>> byClass = new LinkedHashMap<>();

        private Builder() {
        }

        /** Adds one explicit component type. */
        public Builder register(ComponentType<?> type) {
            if (byId.size() >= MAX_TYPES) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                        "register-component-type",
                        "at most 256 component types",
                        Integer.toString(byId.size() + 1),
                        "Use a smaller explicit component vocabulary.");
            }
            if (byId.containsKey(type.id()) || byClass.containsKey(type.valueClass())) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.DUPLICATE_COMPONENT_TYPE,
                        "register-component-type",
                        "unique stable ID and value class",
                        type.id() + ":" + type.valueClass().getName(),
                        "Reuse the existing type or choose a distinct ID and component class.");
            }
            byId.put(type.id(), type);
            byClass.put(type.valueClass(), type);
            return this;
        }

        /** Creates an immutable registry. */
        public ComponentRegistry build() {
            return new ComponentRegistry(byId);
        }
    }
}
