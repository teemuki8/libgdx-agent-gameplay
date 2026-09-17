package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/**
 * Derived pose: the entity's world transform is composed each tick from the parent
 * entity's world transform and this local pose (parent applied after local). The
 * attached entity's {@link Transform3D} is authoritative only through the world's
 * attachment resolution; writing it directly is overwritten on the next tick. When
 * the parent is not active, the child keeps its last resolved pose.
 */
public record AttachedTo(EntityId parent, Transform3D local) implements Component {
    public static final ComponentType<AttachedTo> TYPE =
            new ComponentType<>("attached-to", AttachedTo.class);

    public AttachedTo {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(local, "local");
    }
}
