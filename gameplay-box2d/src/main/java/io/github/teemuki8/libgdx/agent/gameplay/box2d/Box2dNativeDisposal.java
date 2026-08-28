package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import java.util.Objects;

/** Ordered native cleanup that unregisters evidence before destroying owned IDs. */
final class Box2dNativeDisposal {
    private Box2dNativeDisposal() {
    }

    static void destroy(GameplayBox2dWorld world, Box2dBodyHandle handle) {
        Objects.requireNonNull(world, "world").requireUnlocked();
        Box2dBodyHandle checked = Objects.requireNonNull(handle, "handle");
        if (checked.disposed()) {
            return;
        }
        checked.unregisterInspection();
        if (Box2d.b2Shape_IsValid(checked.shape())) {
            Box2d.b2DestroyShape(checked.shape(), true);
        }
        if (Box2d.b2Body_IsValid(checked.body())) {
            Box2d.b2DestroyBody(checked.body());
        }
        checked.markDisposed();
    }
}
