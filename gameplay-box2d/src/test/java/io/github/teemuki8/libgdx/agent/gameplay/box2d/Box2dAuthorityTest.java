package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2dAuthorityTest {
    @BeforeAll static void initializeNatives() { Box2dTestSupport.initializeNatives(); }

    @Test
    void completedNativeStepsCopyDeterministicallyIntoGameplayAuthority() {
        String first = runTranscript();
        assertEquals(first, runTranscript());
        assertNotEquals("0x0.0p0", first);
    }

    private static String runTranscript() {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(new Vec2(0, -9.81));
        AgentRuntime runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body("player", 32, 160)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();
        double y;
        try (GameWorld world = builder.build()) {
            for (int index = 0; index < 12; index++) world.step();
            y = bridge.bodyState(EntityId.of("player")).orElseThrow()
                    .positionRenderUnits().y();
            assertEquals(y, world.snapshot().entity(EntityId.of("player")).orElseThrow()
                    .component(io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D.TYPE)
                    .orElseThrow().position().y());
        }
        runtime.close();
        nativeWorld.close();
        return Double.toHexString(y);
    }
}
