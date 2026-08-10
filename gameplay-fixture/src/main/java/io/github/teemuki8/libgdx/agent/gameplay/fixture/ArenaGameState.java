package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;
import java.util.OptionalLong;

/** Independently owned fixed-tick arena state that is never read back from UI widgets. */
public final class ArenaGameState {
    /** Player-visible arena screens. */
    public enum Screen {
        TITLE,
        PLAYING,
        GAME_OVER
    }

    private Screen screen = Screen.TITLE;
    private Vec2 aimDirection = new Vec2(1, 0);
    private long nextFireTick;
    private long score;
    private boolean enemyKilled;
    private long enemyDeathTick = -1;
    private EntityId enemyKillingSource;
    private boolean playerKilled;

    /** Returns the current independently owned screen state. */
    public Screen screen() {
        return screen;
    }

    /** Enters the actionable production gameplay state. */
    public void startPlaying() {
        screen = Screen.PLAYING;
    }

    /** Returns the current score. */
    public long score() {
        return score;
    }

    /** Returns the latest normalized authored aim direction. */
    public Vec2 aimDirection() {
        return aimDirection;
    }

    /** Applies one normalized authored aim direction from the command stream. */
    public void aim(Vec2 value) {
        Vec2 checked = Objects.requireNonNull(value, "value");
        double length = Math.hypot(checked.x(), checked.y());
        if (length > 0.0) {
            aimDirection = new Vec2(checked.x() / length, checked.y() / length);
        }
    }

    /** Returns whether the fixed-tick weapon cooldown permits one shot. */
    public boolean canFire(long tick) {
        return screen == Screen.PLAYING && tick >= nextFireTick;
    }

    /** Records the next fixed tick on which firing is permitted. */
    public void fired(long tick, long cooldownTicks) {
        nextFireTick = Math.addExact(tick, cooldownTicks);
    }

    /** Reports whether the canonical enemy death transition has occurred. */
    public boolean enemyKilled() {
        return enemyKilled;
    }

    /** Records the one-time enemy reward and attributed killing source. */
    public void killEnemy(long tick, EntityId source) {
        if (!enemyKilled) {
            enemyKilled = true;
            enemyDeathTick = tick;
            enemyKillingSource = Objects.requireNonNull(source, "source");
            score = Math.addExact(score, 300);
        }
    }

    /** Returns the fixed tick on which enemy death began. */
    public OptionalLong enemyDeathTick() {
        return enemyDeathTick < 0 ? OptionalLong.empty() : OptionalLong.of(enemyDeathTick);
    }

    /** Returns the source attributed to the enemy death transition. */
    public EntityId enemyKillingSource() {
        return Objects.requireNonNull(enemyKillingSource, "enemyKillingSource");
    }

    /** Reports whether player failure has occurred. */
    public boolean playerKilled() {
        return playerKilled;
    }

    /** Enters the fixed-tick game-over state once. */
    public void killPlayer() {
        playerKilled = true;
        screen = Screen.GAME_OVER;
    }

    /** Restores the deterministic title-state seed and counters. */
    public void reset() {
        screen = Screen.TITLE;
        aimDirection = new Vec2(1, 0);
        nextFireTick = 0;
        score = 0;
        enemyKilled = false;
        enemyDeathTick = -1;
        enemyKillingSource = null;
        playerKilled = false;
    }

    /** Returns the immutable value copied into each completed gameplay snapshot. */
    public ArenaStateComponent snapshot() {
        return new ArenaStateComponent(
                screen, aimDirection, nextFireTick, score, enemyKilled, enemyDeathTick,
                enemyKillingSource == null ? "none" : enemyKillingSource.value(),
                playerKilled);
    }
}
