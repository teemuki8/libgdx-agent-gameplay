package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.MoveCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaWorldFactory;
import java.util.Objects;

/** Authors bounded enemy pursuit velocity before the authoritative native step. */
public final class EnemyPursuitSystem implements GameSystem {
    private static final CommandSourceId SOURCE = CommandSourceId.of("enemy-ai");
    private final ArenaGameState state;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("enemy-pursuit"), SystemPhase.INPUT, 30);

    /** Creates pursuit against the shared application-owned Box2D bridge. */
    public EnemyPursuitSystem(ArenaGameState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        var player = context.query(Transform2D.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.PLAYER_ID))
                .findFirst();
        var enemy = context.query(Transform2D.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.ENEMY_ID))
                .findFirst();
        Vec2 direction = Vec2.ZERO;
        if (player.isEmpty() || enemy.isEmpty()
                || state.enemyKilled() || state.screen() != ArenaGameState.Screen.PLAYING) {
            enqueue(context, direction);
        } else {
            var from = enemy.orElseThrow().component(Transform2D.TYPE).orElseThrow().position();
            var to = player.orElseThrow().component(Transform2D.TYPE).orElseThrow().position();
            double x = to.x() - from.x();
            double y = to.y() - from.y();
            double length = Math.hypot(x, y);
            if (length > 0.0) {
                direction = new Vec2(x / length, y / length);
            }
            enqueue(context, direction);
        }
    }

    private static void enqueue(SystemContext context, Vec2 direction) {
        context.enqueue(new CommandEnvelope(
                context.tick() + 1, SOURCE, context.tick(),
                new MoveCommand(ArenaWorldFactory.ENEMY_ID, direction)));
    }
}
