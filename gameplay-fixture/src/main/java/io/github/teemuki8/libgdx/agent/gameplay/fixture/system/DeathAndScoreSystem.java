package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityKilled;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaWorldFactory;
import java.util.Objects;

/** Applies one-time death transitions, score reward, and delayed visual despawn. */
public final class DeathAndScoreSystem implements GameSystem {
    private static final long DEATH_ANIMATION_TICKS = 24;
    private final ArenaGameState state;
    private final GameplayBox2dBridge physics;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-death-score"), SystemPhase.GAMEPLAY, 30);

    /** Creates fixed-tick death transitions over the shared physics mapping. */
    public DeathAndScoreSystem(ArenaGameState state, GameplayBox2dBridge physics) {
        this.state = Objects.requireNonNull(state, "state");
        this.physics = Objects.requireNonNull(physics, "physics");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        restoreCompletedHitAnimations(context);
        var enemy = context.query(Health.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.ENEMY_ID))
                .findFirst();
        if (enemy.isPresent()
                && enemy.orElseThrow().component(Health.TYPE).orElseThrow().current() == 0
                && !state.enemyKilled()) {
            EntityId source = lastDamageSource(context, ArenaWorldFactory.ENEMY_ID);
            state.killEnemy(context.tick(), source);
            context.emit(new EntityKilled(ArenaWorldFactory.ENEMY_ID, source));
            physics.deactivate(ArenaWorldFactory.ENEMY_ID);
            enemy.orElseThrow().component(Animation.TYPE).ifPresent(animation ->
                    context.replace(ArenaWorldFactory.ENEMY_ID, Animation.TYPE,
                            new Animation(animation.clips(), "death", 0, 0)));
        }
        if (enemy.isPresent() && state.enemyDeathTick().isPresent()
                && context.tick() - state.enemyDeathTick().orElseThrow()
                        >= DEATH_ANIMATION_TICKS) {
            context.despawn(ArenaWorldFactory.ENEMY_ID);
        }

        var player = context.query(Health.TYPE).stream()
                .filter(entity -> entity.id().equals(ArenaWorldFactory.PLAYER_ID))
                .findFirst();
        if (player.isPresent()
                && player.orElseThrow().component(Health.TYPE).orElseThrow().current() == 0
                && !state.playerKilled()) {
            EntityId source = lastDamageSource(context, ArenaWorldFactory.PLAYER_ID);
            state.killPlayer();
            context.emit(new EntityKilled(ArenaWorldFactory.PLAYER_ID, source));
            physics.stop(ArenaWorldFactory.PLAYER_ID);
        }
    }

    private static void restoreCompletedHitAnimations(SystemContext context) {
        context.query(Animation.TYPE, Health.TYPE).forEach(entity -> {
            Animation animation = entity.component(Animation.TYPE).orElseThrow();
            Health health = entity.component(Health.TYPE).orElseThrow();
            if (health.current() > 0 && "hit".equals(animation.currentClip())
                    && animation.elapsedTicks() >= 4) {
                context.replace(entity.id(), Animation.TYPE,
                        new Animation(animation.clips(), "idle", 0, 0));
            }
        });
    }

    private static EntityId lastDamageSource(SystemContext context, EntityId subject) {
        return context.events().stream().map(event -> event.event())
                .filter(DamageApplied.class::isInstance)
                .map(DamageApplied.class::cast)
                .filter(damage -> damage.subject().equals(subject))
                .reduce((first, second) -> second)
                .map(DamageApplied::source)
                .orElse(subject);
    }
}
