package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Immutable typed three-dimensional fire intent. */
public record Fire3DCommand(EntityId entityId, Vec3 origin, Vec3 direction) implements GameplayCommand {
    /** Requires explicit immutable command values. */
    public Fire3DCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
    }
}
