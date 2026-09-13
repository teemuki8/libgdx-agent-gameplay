package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Primitive collision shape whose size is the complete local-axis bounding box in metres. */
public record BulletBodyShape(Kind kind, Vec3 size) {
    /** Supported bounded primitive families. */
    public enum Kind { BOX, CAPSULE_Y, SPHERE }

    /** Validates positive finite geometry before native narrowing. */
    public BulletBodyShape {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(size, "size");
        double min = Math.min(size.x(), Math.min(size.y(), size.z()));
        double max = Math.max(size.x(), Math.max(size.y(), size.z()));
        if (min < .01 || max > 100) throw new IllegalArgumentException("body shape size must be .01..100 metres");
        if (kind == Kind.CAPSULE_Y && (Math.abs(size.x() - size.z()) > 1e-9 || size.y() < size.x())) {
            throw new IllegalArgumentException("Y capsule requires equal X/Z diameter and height at least diameter");
        }
        if (kind == Kind.SPHERE && (Math.abs(size.x() - size.y()) > 1e-9 || Math.abs(size.x() - size.z()) > 1e-9)) {
            throw new IllegalArgumentException("sphere bounds must be equal");
        }
    }
}
