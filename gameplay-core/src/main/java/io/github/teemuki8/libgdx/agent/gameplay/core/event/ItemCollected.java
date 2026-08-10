package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records collection of an item entity by a subject. */
public record ItemCollected(EntityId subject, EntityId item) implements GameplayEvent {
    /** Rejects null identities. */
    public ItemCollected {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(item, "item");
    }
}
