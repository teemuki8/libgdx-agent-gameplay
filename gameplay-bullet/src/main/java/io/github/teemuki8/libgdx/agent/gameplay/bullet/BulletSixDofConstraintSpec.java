package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Locked translation plus bounded XYZ angular motion between two bodies. */
public record BulletSixDofConstraintSpec(EntityId first, EntityId second, Vec3 anchorWorld,
        QuaternionValue anchorRotationWorld,
        Vec3 angularLowerRadians, Vec3 angularUpperRadians, boolean disableLinkedCollision) {
    /** Validates distinct endpoints, an oriented anchor frame and ordered local-axis angular limits. */
    public BulletSixDofConstraintSpec {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(anchorWorld, "anchorWorld");
        Objects.requireNonNull(anchorRotationWorld, "anchorRotationWorld");
        Objects.requireNonNull(angularLowerRadians, "angularLowerRadians");
        Objects.requireNonNull(angularUpperRadians, "angularUpperRadians");
        if (first.equals(second) || !ordered(angularLowerRadians.x(), angularUpperRadians.x(), Math.PI)
                || !ordered(angularLowerRadians.y(), angularUpperRadians.y(), Math.PI / 2)
                || !ordered(angularLowerRadians.z(), angularUpperRadians.z(), Math.PI)) {
            throw new IllegalArgumentException("invalid six-DOF constraint");
        }
    }

    /** Creates a constraint whose angular axes align with world axes. */
    public BulletSixDofConstraintSpec(EntityId first, EntityId second, Vec3 anchorWorld,
            Vec3 angularLowerRadians, Vec3 angularUpperRadians, boolean disableLinkedCollision) {
        this(first, second, anchorWorld, QuaternionValue.IDENTITY,
                angularLowerRadians, angularUpperRadians, disableLinkedCollision);
    }
    private static boolean ordered(double lower, double upper, double magnitude) {
        return lower >= -magnitude && upper <= magnitude && lower <= upper;
    }
}
