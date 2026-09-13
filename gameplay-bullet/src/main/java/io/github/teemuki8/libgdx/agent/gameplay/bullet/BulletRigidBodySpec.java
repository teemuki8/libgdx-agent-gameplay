package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Complete immutable creation values for one static or dynamic Bullet body. */
public record BulletRigidBodySpec(Transform3D pose, BulletBodyShape shape, double massKilograms,
        double friction, double restitution, double linearDamping, double angularDamping,
        int collisionGroup, int collisionMask) {
    /** Validates SI values, explicit unit scale and unsigned collision bits. */
    public BulletRigidBodySpec {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(shape, "shape");
        if (!pose.scale().equals(Vec3.ONE)) throw new IllegalArgumentException("rigid-body pose scale must be one; shape owns dimensions");
        if (!finite(massKilograms, friction, restitution, linearDamping, angularDamping)
                || massKilograms < 0 || massKilograms > 10000
                || massKilograms > 0 && massKilograms < .001 || friction < 0 || friction > 10
                || restitution < 0 || restitution > 1 || linearDamping < 0 || linearDamping > 1
                || angularDamping < 0 || angularDamping > 1 || collisionGroup < 0 || collisionGroup > 65535
                || collisionMask < 0 || collisionMask > 65535) throw new IllegalArgumentException("invalid rigid-body values");
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
