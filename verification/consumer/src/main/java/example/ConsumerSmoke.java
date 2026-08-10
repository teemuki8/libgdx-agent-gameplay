package example;

import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.FixedStepLoop;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.GameplayRuntimeBridge;
import java.util.List;

/** External compile/runtime proof for all four Maven publications. */
public final class ConsumerSmoke {
    private ConsumerSmoke() {
    }

    /** Resolves every artifact and executes the GL-free public world contract. */
    public static void main(String[] args) {
        List<Class<?>> adapterTypes = List.of(
                FixedStepLoop.class, GameplayRuntimeBridge.class, GameplayBox2dBridge.class);
        try (GameWorld world = GameWorld.builder(
                GameplayLimits.defaults(), StandardComponents.registry()).build()) {
            world.step();
            if (world.snapshot().tick() != 0 || adapterTypes.size() != 3) {
                throw new IllegalStateException("published gameplay contract is inconsistent");
            }
        }
        System.out.println("verified gameplay-core/libgdx/runtime/box2d");
    }
}
