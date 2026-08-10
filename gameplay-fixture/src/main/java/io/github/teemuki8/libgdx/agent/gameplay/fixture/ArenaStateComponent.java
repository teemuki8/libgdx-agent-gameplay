package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable canonical copy of every fixture-owned arena state value. */
public record ArenaStateComponent(
        ArenaGameState.Screen screen,
        Vec2 aimDirection,
        long nextFireTick,
        long score,
        boolean enemyKilled,
        long enemyDeathTick,
        String enemyKillingSource,
        boolean playerKilled) implements Component {
    public static final ComponentType<ArenaStateComponent> TYPE =
            new ComponentType<>("arena-state", ArenaStateComponent.class);

    /** Validates the bounded immutable arena state copy. */
    public ArenaStateComponent {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(aimDirection, "aimDirection");
        enemyKillingSource = Objects.requireNonNull(enemyKillingSource, "enemyKillingSource");
        if (nextFireTick < 0 || score < 0 || enemyDeathTick < -1) {
            throw new IllegalArgumentException("arena state counters must be non-negative");
        }
    }
}
