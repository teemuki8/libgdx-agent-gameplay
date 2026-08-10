package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Requests movement intent for one entity. */
public record MoveCommand(EntityId entityId, Vec2 direction) implements GameplayCommand {
    /** Validates command values. */
    public MoveCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(direction, "direction");
    }
}
