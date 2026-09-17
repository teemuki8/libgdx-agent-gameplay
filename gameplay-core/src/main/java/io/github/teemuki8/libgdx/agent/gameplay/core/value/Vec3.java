package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Objects;

/** Immutable finite three-dimensional vector in application-defined world units. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);

    /** Rejects non-finite coordinates; adapters apply their own world-size limits. */
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-vector3", "finite x, y and z", x + "," + y + "," + z,
                    "Use finite world coordinates.");
        }
    }

    public Vec3 add(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(x + other.x(), y + other.y(), z + other.z());
    }

    public Vec3 subtract(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(x - other.x(), y - other.y(), z - other.z());
    }

    public Vec3 scale(double factor) {
        if (!Double.isFinite(factor)) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "scale-vector3", "finite factor", Double.toString(factor),
                    "Use a finite scale factor.");
        }
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return x * other.x() + y * other.y() + z * other.z();
    }

    public Vec3 cross(Vec3 other) {
        Objects.requireNonNull(other, "other");
        return new Vec3(y * other.z() - z * other.y(),
                z * other.x() - x * other.z(),
                x * other.y() - y * other.x());
    }

    /** Euclidean length in world units; zero for the zero vector. */
    public double length() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    /** Unit vector in the same direction; the zero vector maps to itself. */
    public Vec3 normalized() {
        double length = length();
        if (length < 1e-9) return this;
        return new Vec3(x / length, y / length, z / length);
    }
}
