package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2dDisposalTest {
    @BeforeAll
    static void initializeNatives() {
        Box2dTestSupport.initializeNatives();
    }

    @Test
    void logicalRemovalPrecedesRuntimeCaptureAndNativeDestructionFollowsIt() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        List<String> observed = new ArrayList<>();
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body("player", 32, 32)))
                .lifecycleParticipant(bridge)
                .system(new GameSystem() {
                    @Override public SystemDescriptor descriptor() {
                        return new SystemDescriptor(
                                SystemId.of("remove-player"), SystemPhase.GAMEPLAY, 10);
                    }

                    @Override public void update(SystemContext context) {
                        if (context.tick() == 1) {
                            context.despawn(EntityId.of("player"));
                        }
                    }
                })
                .system(new GameSystem() {
                    @Override public SystemDescriptor descriptor() {
                        return new SystemDescriptor(
                                SystemId.of("observe-native"), SystemPhase.RUNTIME_CAPTURE, 10);
                    }

                    @Override public void update(SystemContext context) {
                        if (context.tick() == 1) {
                            assertFalse(bridge.handle(EntityId.of("player"))
                                    .orElseThrow().body().isActive());
                            observed.add("runtime-capture:" + nativeWorld.getBodyCount());
                        }
                    }
                });
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            world.step();
            observed.add("after-step:" + nativeWorld.getBodyCount());
        }

        assertEquals(List.of("runtime-capture:1", "after-step:0"), observed);
        bridge.close();
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }
}
