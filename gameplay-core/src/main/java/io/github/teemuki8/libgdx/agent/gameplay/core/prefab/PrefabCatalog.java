package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable prefab definitions sorted by semantic ID. */
public final class PrefabCatalog {
    private final Map<PrefabId, PrefabDefinition> definitions;

    PrefabCatalog(Map<PrefabId, PrefabDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new TreeMap<>(definitions));
    }

    /** Returns the required prefab or a typed failure. */
    public PrefabDefinition require(PrefabId id) {
        PrefabDefinition definition = definitions.get(Objects.requireNonNull(id, "id"));
        if (definition == null) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNKNOWN_PREFAB_ID,
                    "resolve-prefab",
                    "one of " + definitions.keySet(),
                    id.value(),
                    "Use a prefab ID declared in the loaded catalog.");
        }
        return definition;
    }

    /** Returns definitions in stable ID order. */
    public List<PrefabDefinition> definitions() {
        return List.copyOf(definitions.values());
    }
}
