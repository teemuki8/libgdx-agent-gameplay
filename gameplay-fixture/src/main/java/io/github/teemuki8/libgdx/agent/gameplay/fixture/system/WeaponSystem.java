package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.FireCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Lifetime;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ProjectileCreated;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabDefinition;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityIdAllocator;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaWorldFactory;
import java.util.Map;
import java.util.Objects;

/** Spawns fixed-speed projectiles and expires them on deterministic simulation ticks. */
public final class WeaponSystem implements GameSystem {
    private static final long FIRE_COOLDOWN_TICKS = 12;
    private static final double PROJECTILE_SPEED = 320.0;
    private static final double MUZZLE_OFFSET = 24.0;

    private final ArenaGameState state;
    private final PrefabDefinition projectile;
    private final EntityIdAllocator ids = new EntityIdAllocator("projectile", 4);
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-weapon"), SystemPhase.GAMEPLAY, 10);

    /** Creates the canonical projectile producer. */
    public WeaponSystem(ArenaGameState state, PrefabDefinition projectile) {
        this.state = Objects.requireNonNull(state, "state");
        this.projectile = Objects.requireNonNull(projectile, "projectile");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        expireProjectiles(context);
        if (!state.canFire(context.tick())) {
            return;
        }
        context.commands().stream().map(command -> command.command())
                .filter(FireCommand.class::isInstance)
                .map(FireCommand.class::cast)
                .filter(command -> command.entityId().equals(ArenaWorldFactory.PLAYER_ID))
                .findFirst().ifPresent(command -> fire(context, command));
    }

    /** Restarts deterministic projectile IDs at a world reset boundary. */
    public void reset() {
        ids.reset();
    }

    private void expireProjectiles(SystemContext context) {
        context.query(Lifetime.TYPE).forEach(entity -> {
            Lifetime lifetime = entity.component(Lifetime.TYPE).orElseThrow();
            if (lifetime.remainingTicks() <= 1) {
                context.despawn(entity.id());
            } else {
                context.replace(entity.id(), Lifetime.TYPE,
                        new Lifetime(lifetime.remainingTicks() - 1));
            }
        });
    }

    private void fire(SystemContext context, FireCommand command) {
        Vec2 direction = normalized(command.direction());
        Vec2 position = new Vec2(
                command.origin().x() + direction.x() * MUZZLE_OFFSET,
                command.origin().y() + direction.y() * MUZZLE_OFFSET);
        var id = ids.next();
        Transform2D transform = new Transform2D(position,
                Math.atan2(direction.y(), direction.x()), new Vec2(12, 12),
                new Vec2(0.5, 0.5));
        Movement movement = new Movement(new Vec2(
                direction.x() * PROJECTILE_SPEED,
                direction.y() * PROJECTILE_SPEED), PROJECTILE_SPEED);
        context.spawn(ArenaWorldFactory.copyWith(projectile, id, Map.of(
                Transform2D.TYPE, transform,
                Movement.TYPE, movement)));
        context.emit(new ProjectileCreated(id, command.entityId()));
        state.fired(context.tick(), FIRE_COOLDOWN_TICKS);
    }

    private static Vec2 normalized(Vec2 direction) {
        double length = Math.hypot(direction.x(), direction.y());
        return length == 0.0 ? stateFallback() : new Vec2(
                direction.x() / length, direction.y() / length);
    }

    private static Vec2 stateFallback() {
        return new Vec2(1, 0);
    }
}
