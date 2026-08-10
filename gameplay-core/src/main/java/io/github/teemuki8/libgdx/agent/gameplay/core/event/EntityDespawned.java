package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records logical removal of one entity. */
public record EntityDespawned(EntityId subject) implements GameplayEvent {
    /** Rejects a null subject. */
    public EntityDespawned {
        Objects.requireNonNull(subject, "subject");
    }
}
