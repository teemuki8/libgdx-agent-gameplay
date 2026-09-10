package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Copied world-space query fact; never contains a native address. */
public record BulletHit(EntityId entity, double fraction, Vec3 point, Vec3 normal) {
    public BulletHit {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) throw new IllegalArgumentException("hit fraction: [0,1]");
    }
}
