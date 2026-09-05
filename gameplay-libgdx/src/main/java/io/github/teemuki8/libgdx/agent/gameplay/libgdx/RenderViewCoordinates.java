package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.RenderView;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.ScreenBounds;
import java.util.Objects;
import java.util.Optional;

/** Render-thread mapping using explicit physical pixels, independent of global window dimensions. */
public final class RenderViewCoordinates {
    private RenderViewCoordinates() {}

    /**
     * Maps top-left framebuffer input to the camera plane, excluding letterbox bars.
     * The application converts logical input pixels to physical framebuffer pixels first and
     * calls this on the render thread with an updated camera.
     */
    public static Optional<Vec2> worldPosition(OrthographicCamera camera, RenderView view, Vec2 input) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(input, "input");
        ScreenBounds visible = view.visibleBounds();
        if (input.x() < visible.minX() || input.y() < visible.minY()
                || input.x() >= visible.maxX() || input.y() >= visible.maxY()) {
            return Optional.empty();
        }
        Vector3 world = new Vector3(
                (float) (2 * (input.x() - view.viewportX()) / view.viewportWidth() - 1),
                (float) (2 * (view.framebufferHeight() - input.y() - view.viewportY())
                        / view.viewportHeight() - 1), -1).prj(camera.invProjectionView);
        return Optional.of(new Vec2(world.x, world.y));
    }
}
