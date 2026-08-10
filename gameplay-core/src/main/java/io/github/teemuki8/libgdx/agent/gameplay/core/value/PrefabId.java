package io.github.teemuki8.libgdx.agent.gameplay.core.value;

/** Stable semantic identity of a prefab. */
public record PrefabId(String value) implements Comparable<PrefabId> {
    /** Validates the semantic ID. */
    public PrefabId {
        value = IdentifierRules.requireIdentifier(value, "prefabId");
    }

    /** Creates a validated prefab ID. */
    public static PrefabId of(String value) {
        return new PrefabId(value);
    }

    @Override
    public int compareTo(PrefabId other) {
        return value.compareTo(other.value);
    }
}
