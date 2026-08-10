package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry of explicit component-to-runtime projections. */
public final class RuntimeProjectionRegistry {
    private static final int MAX_PROJECTIONS = 256;
    private final Map<ComponentType<?>, RuntimeProjection<?>> projections;

    private RuntimeProjectionRegistry(
            Map<ComponentType<?>, RuntimeProjection<?>> projections) {
        this.projections = Collections.unmodifiableMap(new LinkedHashMap<>(projections));
    }

    /** Returns a new bounded registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Resolves the exact projection for a component type. */
    public <T extends Component> RuntimeProjection<T> require(ComponentType<T> type) {
        RuntimeProjection<?> projection = projections.get(Objects.requireNonNull(type, "type"));
        if (projection == null) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNKNOWN_RUNTIME_PROJECTION,
                    "resolve-runtime-projection",
                    "registered projection for " + type.id(),
                    type.id(),
                    "Register an explicit runtime projection before capture.");
        }
        @SuppressWarnings("unchecked")
        RuntimeProjection<T> cast = (RuntimeProjection<T>) projection;
        return cast;
    }

    /** Returns the exact projection when this component is explicitly exposed. */
    public Optional<RuntimeProjection<?>> find(ComponentType<?> type) {
        return Optional.ofNullable(projections.get(Objects.requireNonNull(type, "type")));
    }

    /** Returns projections in deterministic registration order. */
    public List<RuntimeProjection<?>> projections() {
        return List.copyOf(projections.values());
    }

    /** Mutable builder for one immutable projection registry. */
    public static final class Builder {
        private final Map<ComponentType<?>, RuntimeProjection<?>> projections =
                new LinkedHashMap<>();

        private Builder() {
        }

        /** Registers one exact projection transactionally. */
        public Builder register(RuntimeProjection<? extends Component> projection) {
            Objects.requireNonNull(projection, "projection");
            ComponentType<?> type = projection.componentType();
            if (projections.containsKey(type)) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.DUPLICATE_RUNTIME_PROJECTION,
                        "register-runtime-projection",
                        "one projection per component type",
                        type.id(),
                        "Reuse or replace the existing projection before building.");
            }
            if (projections.size() >= MAX_PROJECTIONS) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                        "register-runtime-projection",
                        "at most " + MAX_PROJECTIONS + " projections",
                        Integer.toString(projections.size() + 1),
                        "Use a smaller explicit runtime vocabulary.");
            }
            projections.put(type, projection);
            return this;
        }

        /** Builds an immutable registry. */
        public RuntimeProjectionRegistry build() {
            return new RuntimeProjectionRegistry(projections);
        }
    }
}
