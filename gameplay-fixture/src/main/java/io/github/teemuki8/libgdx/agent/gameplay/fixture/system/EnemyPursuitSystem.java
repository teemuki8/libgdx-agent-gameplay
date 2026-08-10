package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaWorldFactory;
import java.util.Objects;

/** Authors bounded enemy pursuit velocity before the authoritative native step. */
public final class EnemyPursuitSystem implements GameSystem {
    private static final double SPEED = 48.0;
    private final ArenaGameState state;
    private final GameplayBox2dBridge physics;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("enemy-pursuit"), SystemPhase.PRE_PHYSICS, 5);

    /** Creates pursuit against the shared application-owned Box2D bridge. */
    public EnemyPursuitSystem(ArenaGameState state, GameplayBox2dBridge physics) {
        this.state = Objects.requireNonNull(state, "state");
        this.physics = Objects.requireNonNull(physics, "physics");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        var handle = physics.body(ArenaWorldFactory.ENEMY_ID);
        var player = context.query(Transform2D.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.PLAYER_ID))
                .findFirst();
        var enemy = context.query(Transform2D.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.ENEMY_ID))
                .findFirst();
        if (handle.isEmpty() || player.isEmpty() || enemy.isEmpty()
                || state.enemyKilled() || state.screen() != ArenaGameState.Screen.PLAYING) {
            handle.ifPresent(value -> value.body().setLinearVelocity(0, 0));
            return;
        }
        var from = enemy.orElseThrow().component(Transform2D.TYPE).orElseThrow().position();
        var to = player.orElseThrow().component(Transform2D.TYPE).orElseThrow().position();
        double x = to.x() - from.x();
        double y = to.y() - from.y();
        double length = Math.hypot(x, y);
        if (length == 0.0) {
            handle.orElseThrow().body().setLinearVelocity(0, 0);
            return;
        }
        handle.orElseThrow().body().setLinearVelocity(
                (float) physics.units().toPhysicsUnits(x / length * SPEED),
                (float) physics.units().toPhysicsUnits(y / length * SPEED));
    }
}
