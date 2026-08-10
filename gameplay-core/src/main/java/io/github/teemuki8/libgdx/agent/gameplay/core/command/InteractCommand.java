package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Requests an interaction between an actor and target entity. */
public record InteractCommand(EntityId entityId, EntityId targetId) implements GameplayCommand {
    /** Validates command identities. */
    public InteractCommand {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(targetId, "targetId");
    }
}
