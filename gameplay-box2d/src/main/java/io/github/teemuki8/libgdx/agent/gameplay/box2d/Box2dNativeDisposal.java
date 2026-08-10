package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.World;
import java.util.Objects;

/** Ordered native cleanup that unregisters evidence before destroying bridge-created objects. */
final class Box2dNativeDisposal {
    private Box2dNativeDisposal() {
    }

    /** Unregisters fixture/body evidence and destroys the containing body exactly once. */
    static void destroy(World world, Box2dBodyHandle handle) {
        Objects.requireNonNull(world, "world");
        Box2dBodyHandle checked = Objects.requireNonNull(handle, "handle");
        if (checked.disposed()) {
            return;
        }
        checked.unregisterInspection();
        world.destroyBody(checked.body());
        checked.markDisposed();
    }
}
