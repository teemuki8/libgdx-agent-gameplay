package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Requests an aim direction for one entity. */
public record AimCommand(EntityId entityId, Vec2 direction) implements GameplayCommand {
    /** Validates command values. */
    public AimCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(direction, "direction");
    }
}
