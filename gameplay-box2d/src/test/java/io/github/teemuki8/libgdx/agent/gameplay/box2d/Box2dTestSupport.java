package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;

final class Box2dTestSupport {
    static final long STEP_NANOS = 16_666_667L;
    static final Box2dUnitConversion UNITS = new Box2dUnitConversion(32.0);

    private Box2dTestSupport() {
    }

    static void initializeNatives() { Box2d.initialize(); }
    static AgentRuntime runtime() { return AgentRuntime.builder().build(); }
    static GameplayLimits limits() { return GameplayLimits.defaults(); }

    static GameplayBox2dWorld world(Vec2 gravity) {
        return GameplayBox2dWorld.create(new Box2dWorldSpec(gravity, 4, 0.1));
    }

    static Box2dBodyFactory dynamicBodies() {
        return new Box2dBodyFactory(UNITS, ignored -> new Box2dBodySpec(
                Box2dBodyType.DYNAMIC, 1.0, 0.4, 0.0,
                0.0, 0.0, 1.0, false, false));
    }

    static GameplayBox2dBridge bridge(GameplayBox2dWorld world, AgentRuntime runtime) {
        return new GameplayBox2dBridge(world, dynamicBodies(), UNITS, runtime, limits());
    }

    static EntityDraft body(String id, double x, double y) {
        return body(id, x, y, Collider.Shape.BOX, new Vec2(28, 28));
    }

    static EntityDraft body(String id, double x, double y, Collider.Shape shape, Vec2 size) {
        return EntityDraft.builder(EntityId.of(id))
                .with(Transform2D.TYPE, new Transform2D(
                        new Vec2(x, y), 0.0, size, new Vec2(0.5, 0.5)))
                .with(Movement.TYPE, new Movement(Vec2.ZERO, 64.0))
                .with(Collider.TYPE, new Collider(shape, size, Vec2.ZERO,
                        false, 1, 0xffff))
                .build();
    }
}
