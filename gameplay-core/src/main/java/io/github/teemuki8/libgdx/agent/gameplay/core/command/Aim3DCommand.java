package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Aim3D;
import java.util.Objects;

/** Immutable typed three-dimensional aim intent. */
public record Aim3DCommand(EntityId entityId, Aim3D aim) implements GameplayCommand {
    /** Requires explicit immutable command values. */
    public Aim3DCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(aim, "aim");
    }
}
