package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.VisualSnapshotBuilder;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.GameplayRuntimeBridge;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityRegistration;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityType;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import java.util.List;
import java.util.Objects;

/** Publishes fixture domain values and prepares immutable visual evidence before capture. */
public final class ArenaRuntimeProjection implements GameSystem, AutoCloseable {
    private static final EntityType ARENA_TYPE = EntityType.of("arena-state");
    private static final EntityType ACTOR_TYPE = EntityType.of("arena-actor");

    private final ArenaGameState state;
    private final GameplayRuntimeBridge bridge;
    private final VisualSnapshotBuilder visuals;
    private final List<EntityRegistration> registrations;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-visual-evidence"), SystemPhase.RENDER_PREP, 10);
    private volatile WorldSnapshot snapshot = new WorldSnapshot(0, List.of());
    private boolean closed;

    /** Registers stable fixture aliases before the caller starts the runtime. */
    public ArenaRuntimeProjection(
            AgentRuntime runtime,
            ArenaGameState state,
            GameplayRuntimeBridge bridge,
            VisualSnapshotBuilder visuals) {
        AgentRuntime checkedRuntime = Objects.requireNonNull(runtime, "runtime");
        this.state = Objects.requireNonNull(state, "state");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        EntityRegistration arena = checkedRuntime.entities().register(
                EntityId.of("gameplay-arena"), ARENA_TYPE, () -> "Arena state",
                inspector -> {
                    inspector.property("screen",
                            () -> RuntimeValues.enumValue(this.state.screen().name()));
                    inspector.property("score",
                            () -> RuntimeValues.integer(this.state.score()));
                });
        EntityRegistration player = null;
        EntityRegistration enemy = null;
        try {
            player = registerActor(checkedRuntime, "gameplay-player", "Player",
                    ArenaWorldFactory.PLAYER_ID);
            enemy = registerActor(checkedRuntime, "gameplay-enemy", "Enemy",
                    ArenaWorldFactory.ENEMY_ID);
            registrations = List.of(arena, player, enemy);
        } catch (RuntimeException | Error failure) {
            if (enemy != null) {
                enemy.close();
            }
            if (player != null) {
                player.close();
            }
            arena.close();
            throw failure;
        }
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    /** Captures final tick state and visual geometry before runtime capture. */
    @Override public void update(SystemContext context) {
        WorldSnapshot current = context.snapshot();
        snapshot = current;
        bridge.prepareVisuals(visuals.build(current));
    }

    /** Closes only projection-owned registrations in reverse acquisition order. */
    @Override public void close() {
        if (closed) {
            return;
        }
        for (int index = registrations.size() - 1; index >= 0; index--) {
            registrations.get(index).close();
        }
        closed = true;
    }

    private EntityRegistration registerActor(
            AgentRuntime runtime,
            String id,
            String name,
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId entityId) {
        return runtime.entities().register(EntityId.of(id), ACTOR_TYPE, () -> name,
                inspector -> {
                    inspector.property("health-current", () -> RuntimeValues.integer(
                            health(entityId, false)));
                    inspector.property("health-max", () -> RuntimeValues.integer(
                            health(entityId, true)));
                });
    }

    private long health(
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId id,
            boolean maximum) {
        return snapshot.entity(id)
                .flatMap(entity -> entity.component(Health.TYPE))
                .map(value -> maximum ? value.max() : value.current())
                .orElse(0L);
    }
}
