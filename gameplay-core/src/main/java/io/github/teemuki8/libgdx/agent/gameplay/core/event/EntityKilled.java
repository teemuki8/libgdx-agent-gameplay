package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records a source-attributed entity death. */
public record EntityKilled(EntityId subject, EntityId source) implements GameplayEvent {
    /** Rejects null identities. */
    public EntityKilled {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
    }
}
