package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import java.util.Objects;

/** Immutable backend-neutral body, material, and dynamics specification. */
public record Box2dBodySpec(
        Box2dBodyType type,
        double densityKilogramsPerSquareMetre,
        double friction,
        double restitution,
        double linearDamping,
        double angularDamping,
        double gravityScale,
        boolean bullet,
        boolean fixedRotation) {
    /** Validates all copied native inputs before narrowing. */
    public Box2dBodySpec {
        Objects.requireNonNull(type, "type");
        finiteNonNegative(densityKilogramsPerSquareMetre, "density");
        finiteNonNegative(friction, "friction");
        finiteNonNegative(linearDamping, "linearDamping");
        finiteNonNegative(angularDamping, "angularDamping");
        finiteNonNegative(gravityScale, "gravityScale");
        if (!Double.isFinite(restitution) || restitution < 0.0 || restitution > 1.0) {
            throw new IllegalArgumentException("restitution must be finite and in [0,1]");
        }
        if (type == Box2dBodyType.DYNAMIC && densityKilogramsPerSquareMetre <= 0.0) {
            throw new IllegalArgumentException("dynamic body density must be positive");
        }
    }

    private static void finiteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }
}
