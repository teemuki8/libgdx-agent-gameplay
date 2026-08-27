package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.box2d.Box2d;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2dDisposalTest {
    @BeforeAll static void initializeNatives() { Box2dTestSupport.initializeNatives(); }

    @Test
    void bridgeDestroysJointsBeforeBodiesAndLeavesApplicationWorldLive() {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(Vec2.ZERO);
        AgentRuntime runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("first", 0, 0));
                    sink.spawn(Box2dTestSupport.body("second", 32, 0));
                }).lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();
        com.badlogic.gdx.box2d.structs.b2BodyId firstBody;
        var counters = new com.badlogic.gdx.box2d.structs.b2Counters();
        try (GameWorld world = builder.build()) {
            world.step();
            firstBody = bridge.handle(EntityId.of("first")).orElseThrow().body();
            Box2dJointId id = Box2dJointId.of("connected");
            bridge.createRevoluteJoint(new Box2dRevoluteJointSpec(id,
                    EntityId.of("first"), EntityId.of("second"),
                    new Vec2(16, 0), -0.2, 0.2, false));
            assertTrue(Box2d.b2Body_IsValid(firstBody));
            Box2d.b2World_GetCounters(nativeWorld.id(), counters);
            assertEquals(2, counters.bodyCount());
            assertEquals(1, counters.jointCount());
        }
        assertFalse(Box2d.b2Body_IsValid(firstBody));
        Box2d.b2World_GetCounters(nativeWorld.id(), counters);
        assertEquals(0, counters.bodyCount());
        assertEquals(0, counters.jointCount());
        assertTrue(Box2d.b2World_IsValid(nativeWorld.id()));
        bridge.close();
        runtime.close();
        nativeWorld.close();
    }
}
