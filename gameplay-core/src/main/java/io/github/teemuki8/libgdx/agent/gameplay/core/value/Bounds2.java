package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Immutable finite axis-aligned bounds. */
public record Bounds2(double minX, double minY, double maxX, double maxY) {
    /** Validates finite ordered bounds. */
    public Bounds2 {
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || maxX < minX || maxY < minY) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-bounds",
                    "finite min values no greater than max values",
                    minX + "," + minY + "," + maxX + "," + maxY,
                    "Use ordered finite world or screen bounds.");
        }
    }

    /** Returns the horizontal extent. */
    public double width() {
        return maxX - minX;
    }

    /** Returns the vertical extent. */
    public double height() {
        return maxY - minY;
    }

    /** Returns whether these bounds overlap another inclusive rectangle. */
    public boolean intersects(Bounds2 other) {
        return maxX >= other.minX && other.maxX >= minX
                && maxY >= other.minY && other.maxY >= minY;
    }
}
