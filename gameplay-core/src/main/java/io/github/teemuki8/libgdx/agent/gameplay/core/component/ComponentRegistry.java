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
    private final Map<String, ComponentCodec<?>> codecs;

    private ComponentRegistry(
            Map<String, ComponentType<?>> byId,
            Map<String, ComponentCodec<?>> codecs) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.codecs = Collections.unmodifiableMap(new LinkedHashMap<>(codecs));
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

    /** Produces a detached immutable value using the registered explicit codec. */
    public <T extends Component> T snapshot(ComponentType<T> type, T value) {
        return codec(type).snapshot(type.valueClass().cast(value));
    }

    /** Canonically encodes a component through its registered explicit codec. */
    public <T extends Component> void encode(
            ComponentType<T> type, T value, CanonicalComponentWriter writer) {
        codec(type).encode(type.valueClass().cast(value), writer);
    }

    private <T extends Component> ComponentCodec<T> codec(ComponentType<T> type) {
        require(type.id());
        @SuppressWarnings("unchecked")
        ComponentCodec<T> codec = (ComponentCodec<T>) codecs.get(type.id());
        return codec;
    }

    /** Mutable single-use builder for an immutable registry. */
    public static final class Builder {
        private final Map<String, ComponentType<?>> byId = new LinkedHashMap<>();
        private final Map<Class<?>, ComponentType<?>> byClass = new LinkedHashMap<>();
        private final Map<String, ComponentCodec<?>> codecs = new LinkedHashMap<>();

        private Builder() {
        }

        /** Adds one explicit component type. */
        public Builder register(ComponentType<?> type) {
            rejectDuplicate(type);
            return registerUnchecked(type, StandardComponents.codec(type));
        }

        /** Adds a custom type with mandatory immutable snapshot and canonical codecs. */
        public <T extends Component> Builder register(
                ComponentType<T> type, ComponentCodec<T> codec) {
            return registerUnchecked(type, codec);
        }

        private Builder registerUnchecked(ComponentType<?> type, ComponentCodec<?> codec) {
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
            codecs.put(type.id(), java.util.Objects.requireNonNull(codec, "codec"));
            return this;
        }

        private void rejectDuplicate(ComponentType<?> type) {
            if (byId.containsKey(type.id()) || byClass.containsKey(type.valueClass())) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.DUPLICATE_COMPONENT_TYPE,
                        "register-component-type",
                        "unique stable ID and value class",
                        type.id() + ":" + type.valueClass().getName(),
                        "Reuse the existing type or choose a distinct ID and component class.");
            }
        }

        /** Creates an immutable registry. */
        public ComponentRegistry build() {
            return new ComponentRegistry(byId, codecs);
        }
    }
}
