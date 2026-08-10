package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records activation of one entity. */
public record EntitySpawned(EntityId subject) implements GameplayEvent {
    /** Rejects a null subject. */
    public EntitySpawned {
        Objects.requireNonNull(subject, "subject");
    }
}
