package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GameplayBox2dBridgeTest {
    @BeforeAll
    static void initializeNatives() {
        Box2dTestSupport.initializeNatives();
    }

    @Test
    void activationCreatesStableInspectedBodyWithoutTakingWorldOwnership() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body("player", 32, 64)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            Box2dBodyHandle handle = bridge.body(EntityId.of("player")).orElseThrow();
            assertEquals(EntityId.of("player"), handle.body().getUserData());
            assertEquals(1, nativeWorld.getBodyCount());
            runtime.frame(1, () -> { });
            assertEquals("player", runtime.entity(runtimeId("box2d.body.player"))
                    .orElseThrow().displayName().orElseThrow());
            assertEquals("player.collider", runtime.entity(
                    runtimeId("box2d.fixture.player.collider"))
                    .orElseThrow().displayName().orElseThrow());
            assertEquals("gameplay", runtime.entity(runtimeId("box2d.contacts.gameplay"))
                    .orElseThrow().displayName().orElseThrow());
        }

        assertEquals(0, nativeWorld.getBodyCount());
        nativeWorld.createBody(new com.badlogic.gdx.physics.box2d.BodyDef());
        assertEquals(1, nativeWorld.getBodyCount());
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId runtimeId(String value) {
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }
}
