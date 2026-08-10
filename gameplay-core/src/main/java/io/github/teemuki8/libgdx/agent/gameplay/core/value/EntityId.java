package io.github.teemuki8.libgdx.agent.gameplay.core.value;

/** Stable semantic identity of a gameplay entity. */
public record EntityId(String value) implements Comparable<EntityId> {
    /** Validates the semantic ID. */
    public EntityId {
        value = IdentifierRules.requireIdentifier(value, "entityId");
    }

    /** Creates a validated entity ID. */
    public static EntityId of(String value) {
        return new EntityId(value);
    }

    @Override
    public int compareTo(EntityId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
