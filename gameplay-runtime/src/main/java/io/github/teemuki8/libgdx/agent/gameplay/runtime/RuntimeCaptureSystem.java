package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.Objects;

/** Completes one agent-runtime frame after visual preparation. */
public final class RuntimeCaptureSystem implements GameSystem {
    private final GameplayRuntimeBridge bridge;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("runtime-capture"), SystemPhase.RUNTIME_CAPTURE,
            SystemDescriptor.MAX_SLOT);

    RuntimeCaptureSystem(GameplayRuntimeBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @Override
    public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void update(SystemContext context) {
        bridge.capture(new GameplayRuntimeFrame(
                context.snapshot(),
                context.commands(),
                context.events(),
                bridge.requirePreparedVisuals(context.tick()),
                "gameplay-frame-" + context.tick()));
    }
}
