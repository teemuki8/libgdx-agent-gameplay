package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.Objects;

/** Opens one agent-runtime frame before gameplay input systems execute. */
public final class RuntimeOpenFrameSystem implements GameSystem {
    private final GameplayRuntimeBridge bridge;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("runtime-open-frame"), SystemPhase.INPUT, 0);

    RuntimeOpenFrameSystem(GameplayRuntimeBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void update(SystemContext context) {
        bridge.openFrame(context.tick(), context.fixedStepNanos());
    }
}
