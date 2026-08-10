package io.github.teemuki8.libgdx.agent.gameplay.core.value;

/** Stable semantic identity of a gameplay system. */
public record SystemId(String value) implements Comparable<SystemId> {
    /** Validates the semantic ID. */
    public SystemId {
        value = IdentifierRules.requireIdentifier(value, "systemId");
    }

    /** Creates a validated system ID. */
    public static SystemId of(String value) {
        return new SystemId(value);
    }

    @Override
    public int compareTo(SystemId other) {
        return value.compareTo(other.value);
    }
}
