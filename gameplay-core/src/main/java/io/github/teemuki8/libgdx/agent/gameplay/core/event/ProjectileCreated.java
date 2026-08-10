package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records creation of a projectile by its source entity. */
public record ProjectileCreated(EntityId subject, EntityId source) implements GameplayEvent {
    /** Rejects null identities. */
    public ProjectileCreated {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
    }
}
