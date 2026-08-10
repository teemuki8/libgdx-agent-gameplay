package io.github.teemuki8.libgdx.agent.gameplay.core.value;

/** Stable identity of a command producer. */
public record CommandSourceId(String value) implements Comparable<CommandSourceId> {
    /** Validates the semantic ID. */
    public CommandSourceId {
        value = IdentifierRules.requireIdentifier(value, "commandSourceId");
    }

    /** Creates a validated command source ID. */
    public static CommandSourceId of(String value) {
        return new CommandSourceId(value);
    }

    @Override
    public int compareTo(CommandSourceId other) {
        return value.compareTo(other.value);
    }
}
