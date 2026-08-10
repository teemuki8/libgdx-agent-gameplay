package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.MoveCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2dAuthorityTest {
    @BeforeAll
    static void initializeNatives() {
        Box2dTestSupport.initializeNatives();
    }

    @Test
    void bodyPoseAndVelocityAreCopiedBackAfterTheNativeStep() {
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
            EntityId player = EntityId.of("player");
            world.enqueue(new CommandEnvelope(0, CommandSourceId.of("keyboard"), 0,
                    new MoveCommand(player, new Vec2(1, 0))));
            var completed = world.step();
            Transform2D transform = completed.snapshot().entity(player).orElseThrow()
                    .component(Transform2D.TYPE).orElseThrow();
            Movement movement = completed.snapshot().entity(player).orElseThrow()
                    .component(Movement.TYPE).orElseThrow();
            Vector2 nativePosition = bridge.body(player).orElseThrow().body().getPosition();

            assertEquals(Box2dTestSupport.UNITS.toRenderUnits(nativePosition.x),
                    transform.position().x(), 0.0001);
            assertEquals(Box2dTestSupport.UNITS.toRenderUnits(nativePosition.y),
                    transform.position().y(), 0.0001);
            assertEquals(64.0, movement.velocity().x(), 0.0001);
            assertEquals(0.0, movement.velocity().y(), 0.0001);
        }
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }
}
