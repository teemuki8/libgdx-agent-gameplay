package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Requests one named ability at an explicit world target. */
public record UseAbilityCommand(EntityId entityId, String abilityId, Vec2 target)
        implements GameplayCommand {
    /** Validates command values. */
    public UseAbilityCommand {
        Objects.requireNonNull(entityId, "entityId");
        abilityId = IdentifierRules.requireIdentifier(abilityId, "abilityId");
        Objects.requireNonNull(target, "target");
    }
}
