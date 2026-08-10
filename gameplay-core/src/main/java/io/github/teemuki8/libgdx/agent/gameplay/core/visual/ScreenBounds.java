package io.github.teemuki8.libgdx.agent.gameplay.core.visual;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Finite ordered framebuffer bounds using a top-left origin. */
public record ScreenBounds(double minX, double minY, double maxX, double maxY) {
    /** Validates finite ordered coordinates. */
    public ScreenBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || maxX < minX || maxY < minY) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNPROJECTABLE_BOUNDS,
                    "create-screen-bounds",
                    "finite top-left bounds with min <= max",
                    minX + "," + minY + "," + maxX + "," + maxY,
                    "Project finite world bounds through an updated camera.");
        }
    }

    /** Returns whether these bounds intersect the inclusive framebuffer rectangle. */
    public boolean intersects(double width, double height) {
        return maxX >= 0 && maxY >= 0 && minX <= width && minY <= height;
    }
}
