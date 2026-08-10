package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable registry of explicit component codecs keyed by stable type ID. */
public final class ComponentCodecRegistry {
    private final Map<String, ComponentCodec<?>> codecs;

    private ComponentCodecRegistry(Map<String, ComponentCodec<?>> codecs) {
        this.codecs = Collections.unmodifiableMap(new LinkedHashMap<>(codecs));
    }

    /** Returns an empty registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Resolves a codec by stable component type ID. */
    public ComponentCodec<?> require(String id) {
        String validated = IdentifierRules.requireIdentifier(id, "componentType");
        ComponentCodec<?> codec = codecs.get(validated);
        if (codec == null) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNKNOWN_COMPONENT_TYPE,
                    "resolve-component-codec",
                    "one of " + codecs.keySet(),
                    validated,
                    "Use a registered standard or application component type.");
        }
        return codec;
    }

    /** Returns codecs in deterministic registration order. */
    public List<ComponentCodec<?>> codecs() {
        return List.copyOf(codecs.values());
    }

    /** Mutable single-use registry builder. */
    public static final class Builder {
        private final Map<String, ComponentCodec<?>> codecs = new LinkedHashMap<>();

        private Builder() {
        }

        /** Registers one explicit codec transactionally. */
        public Builder register(ComponentCodec<? extends Component> codec) {
            String id = codec.type().id();
            if (codecs.containsKey(id)) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.DUPLICATE_COMPONENT_TYPE,
                        "register-component-codec",
                        "unique component type ID",
                        id,
                        "Register exactly one codec for each component type.");
            }
            codecs.put(id, codec);
            return this;
        }

        /** Builds an immutable codec registry. */
        public ComponentCodecRegistry build() {
            return new ComponentCodecRegistry(codecs);
        }
    }
}
