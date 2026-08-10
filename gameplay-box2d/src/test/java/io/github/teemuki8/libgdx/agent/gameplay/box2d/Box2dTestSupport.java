package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.utils.GdxNativesLoader;
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
    static final Box2dSolverSettings SOLVER = new Box2dSolverSettings(6, 2);

    private Box2dTestSupport() {
    }

    static void initializeNatives() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    static Box2dBodyFactory dynamicBodies() {
        return new Box2dBodyFactory(UNITS, ignored -> BodyDef.BodyType.DynamicBody);
    }

    static GameplayBox2dBridge bridge(
            com.badlogic.gdx.physics.box2d.World world, AgentRuntime runtime) {
        return new GameplayBox2dBridge(world, dynamicBodies(), UNITS, SOLVER,
                runtime, GameplayLimits.defaults());
    }

    static EntityDraft body(String id, double x, double y) {
        return EntityDraft.builder(EntityId.of(id))
                .with(Transform2D.TYPE, new Transform2D(
                        new Vec2(x, y), 0.0, new Vec2(32, 32), new Vec2(0.5, 0.5)))
                .with(Movement.TYPE, new Movement(Vec2.ZERO, 64.0))
                .with(Collider.TYPE, new Collider(
                        Collider.Shape.BOX, new Vec2(28, 28), Vec2.ZERO,
                        false, 1, 0xffff))
                .build();
    }
}
