package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Requests a fire action from an explicit origin and direction. */
public record FireCommand(EntityId entityId, Vec2 origin, Vec2 direction)
        implements GameplayCommand {
    /** Validates command values. */
    public FireCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
    }
}
