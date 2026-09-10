package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

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
}
