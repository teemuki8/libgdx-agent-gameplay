package io.github.teemuki8.libgdx.agent.gameplay.core.visual;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Objects;

/** Copied physical-pixel framebuffer and bottom-left GL viewport geometry. */
public record RenderView(int framebufferWidth, int framebufferHeight,
        int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
    /** Validates positive dimensions and a viewport intersecting the framebuffer. */
    public RenderView {
        if (framebufferWidth < 1 || framebufferHeight < 1
                || viewportWidth < 1 || viewportHeight < 1
                || (long) viewportX + viewportWidth <= 0
                || (long) viewportY + viewportHeight <= 0
                || viewportX >= framebufferWidth || viewportY >= framebufferHeight) {
            throw GameplayException.validation(GameplayDiagnosticCode.UNPROJECTABLE_BOUNDS,
                    "configure-render-view", "positive framebuffer and intersecting viewport",
                    framebufferWidth + "x" + framebufferHeight + ":" + viewportX + "," + viewportY
                            + ":" + viewportWidth + "x" + viewportHeight,
                    "Copy the current physical framebuffer and GL viewport after resize.");
        }
    }

    /** Describes the legacy full-framebuffer view. */
    public static RenderView fullFramebuffer(int width, int height) {
        return new RenderView(width, height, 0, 0, width, height);
    }

    /** Returns the visible viewport clipped to the framebuffer, with a top-left origin. */
    public ScreenBounds visibleBounds() {
        return new ScreenBounds(Math.max(0, viewportX),
                framebufferHeight - Math.min((long) framebufferHeight, (long) viewportY + viewportHeight),
                Math.min((long) framebufferWidth, (long) viewportX + viewportWidth),
                framebufferHeight - Math.max(0, viewportY));
    }

    /** Reports intersection with the actual visible viewport, excluding letterbox bars. */
    public boolean intersects(ScreenBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        ScreenBounds visible = visibleBounds();
        return bounds.maxX() >= visible.minX() && bounds.maxY() >= visible.minY()
                && bounds.minX() <= visible.maxX() && bounds.minY() <= visible.maxY();
    }
}
