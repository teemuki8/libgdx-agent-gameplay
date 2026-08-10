package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;

/** Stable semantic faction membership. */
public record Faction(String value) implements Component {
    public static final ComponentType<Faction> TYPE =
            new ComponentType<>("faction", Faction.class);

    /** Validates the faction ID. */
    public Faction {
        value = IdentifierRules.requireIdentifier(value, "faction");
    }
}
