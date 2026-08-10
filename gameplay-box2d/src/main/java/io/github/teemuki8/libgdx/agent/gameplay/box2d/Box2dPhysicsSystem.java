package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.Objects;

/** Executes the caller-owned Box2D world's fixed native step in the PHYSICS phase. */
public final class Box2dPhysicsSystem implements GameSystem {
    private final GameplayBox2dBridge bridge;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("box2d-world-step"), SystemPhase.PHYSICS, 10);

    Box2dPhysicsSystem(GameplayBox2dBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void update(SystemContext context) {
        bridge.stepPhysics(context);
    }
}
