package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Objects;

/**
 * Immutable unit quaternion in x/y/z/w order, using right-handed active rotations.
 * Components are retained verbatim; callers normalize before construction. The squared
 * length tolerance of 1e-6 admits copied native float rotations without silent repair.
 */
public record QuaternionValue(double x, double y, double z, double w) {
    public static final QuaternionValue IDENTITY = new QuaternionValue(0, 0, 0, 1);

    /** Rejects non-finite or non-unit rotations. */
    public QuaternionValue {
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(lengthSquared) || Math.abs(lengthSquared - 1) > 1e-6) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-quaternion", "unit quaternion within squared-length tolerance 1e-6",
                    x + "," + y + "," + z + "," + w,
                    "Normalize a finite nonzero quaternion before construction.");
        }
    }

    /** Rotates the vector by this quaternion (right-handed active rotation). */
    public Vec3 rotate(Vec3 value) {
        Objects.requireNonNull(value, "value");
        Vec3 axis = new Vec3(x, y, z);
        Vec3 first = axis.cross(value);
        Vec3 second = axis.cross(first);
        return value.add(first.scale(2 * w)).add(second.scale(2));
    }

    /** Hamilton product: the rotation applied by this AFTER {@code other}. */
    public QuaternionValue multiply(QuaternionValue other) {
        Objects.requireNonNull(other, "other");
        return new QuaternionValue(
                w * other.x() + x * other.w() + y * other.z() - z * other.y(),
                w * other.y() - x * other.z() + y * other.w() + z * other.x(),
                w * other.z() + x * other.y() - y * other.x() + z * other.w(),
                w * other.w() - x * other.x() - y * other.y() - z * other.z());
    }

    /** Unit quaternion rotating by {@code angleRadians} about the finite nonzero {@code axis}. */
    public static QuaternionValue fromAxisAngle(Vec3 axis, double angleRadians) {
        Objects.requireNonNull(axis, "axis");
        double length = Math.hypot(Math.hypot(axis.x(), axis.y()), axis.z());
        if (!Double.isFinite(length) || length < 1e-9) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "from-axis-angle", "finite nonzero axis",
                    axis.x() + "," + axis.y() + "," + axis.z(),
                    "Provide a finite nonzero axis; it is normalized internally.");
        }
        if (!Double.isFinite(angleRadians)) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "from-axis-angle", "finite angle in radians", Double.toString(angleRadians),
                    "Provide a finite angle.");
        }
        double half = angleRadians / 2;
        double scale = Math.sin(half) / length;
        return new QuaternionValue(axis.x() * scale, axis.y() * scale, axis.z() * scale, Math.cos(half));
    }

}
