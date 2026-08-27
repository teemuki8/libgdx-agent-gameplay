package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.structs.b2ContactData;
import com.badlogic.gdx.box2d.structs.b2ContactEvents;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionImpact;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2dContactCollectorTest {
    @BeforeAll static void initializeNatives() { Box2dTestSupport.initializeNatives(); }

    @Test
    void fourSubstepHitUsesMaximumTotalNormalImpulseAndStableEndpoints() {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(new Vec2(0, -10));
        AgentRuntime runtime = Box2dTestSupport.runtime();
        Box2dBodyFactory factory = new Box2dBodyFactory(Box2dTestSupport.UNITS, entity ->
                entity.id().value().equals("ground")
                        ? new Box2dBodySpec(Box2dBodyType.STATIC, 0, 0.4, 0,
                                0, 0, 1, false, false)
                        : new Box2dBodySpec(Box2dBodyType.DYNAMIC, 2, 0.4, 0,
                                0, 0, 1, false, false));
        GameplayBox2dBridge bridge = new GameplayBox2dBridge(nativeWorld, factory,
                Box2dTestSupport.UNITS, runtime, GameplayLimits.defaults());
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("capsule", 0, 96,
                            Collider.Shape.CAPSULE, new Vec2(16, 48)));
                    sink.spawn(Box2dTestSupport.body("ground", 0, 0,
                            Collider.Shape.BOX, new Vec2(256, 16)));
                }).lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        CollisionImpact impact = null;
        double copiedTotal = 0.0;
        double copiedLastSubstep = 0.0;
        try (GameWorld world = builder.build()) {
            for (int step = 0; step < 180 && impact == null; step++) {
                var completed = world.step();
                impact = completed.events().stream().map(value -> value.event())
                        .filter(CollisionImpact.class::isInstance)
                        .map(CollisionImpact.class::cast).findFirst().orElse(null);
                if (impact != null) {
                    b2ContactEvents events = new b2ContactEvents();
                    Box2d.b2World_GetContactEvents(nativeWorld.id(), events);
                    assertTrue(events.hitCount() > 0);
                    var hit = events.hitEvents().asStackElement(0);
                    int capacity = Box2d.b2Shape_GetContactCapacity(hit.shapeIdA());
                    var buffer = new b2ContactData.b2ContactDataPointer(capacity, false);
                    try {
                        int count = Box2d.b2Shape_GetContactData(hit.shapeIdA(), buffer, capacity);
                        for (int contactIndex = 0; contactIndex < count; contactIndex++) {
                            var manifold = buffer.asStackElement(contactIndex).manifold();
                            for (int pointIndex = 0; pointIndex < manifold.pointCount(); pointIndex++) {
                                var point = manifold.points().asStackElement(pointIndex);
                                copiedLastSubstep = Math.max(copiedLastSubstep,
                                        point.normalImpulse());
                                copiedTotal = Math.max(copiedTotal,
                                        point.totalNormalImpulse());
                            }
                        }
                    } finally {
                        buffer.free();
                    }
                }
            }
        }
        assertNotNull(impact);
        assertEquals(EntityId.of("capsule"), impact.first());
        assertEquals(EntityId.of("ground"), impact.second());
        assertTrue(copiedTotal > 0.0);
        assertEquals(copiedTotal, impact.normalImpulse(), 1.0e-6);
        assertTrue(copiedTotal >= copiedLastSubstep);
        runtime.close();
        nativeWorld.close();
    }
}
