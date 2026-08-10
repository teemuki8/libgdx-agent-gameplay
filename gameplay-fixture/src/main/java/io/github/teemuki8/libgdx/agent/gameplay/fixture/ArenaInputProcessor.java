package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.AimCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.FireCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.MoveCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import java.util.Objects;

/** Production WASD/Space input path that enqueues deterministic keyboard commands. */
public final class ArenaInputProcessor extends InputAdapter {
    private static final CommandSourceId KEYBOARD = CommandSourceId.of("keyboard");

    private final GameWorld world;
    private final ArenaGameState state;
    private long sequence;
    private long lastMovementTarget = -1;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean fire;

    /** Binds production input to the owner-thread world command queue. */
    public ArenaInputProcessor(GameWorld world, ArenaGameState state) {
        this.world = Objects.requireNonNull(world, "world");
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public boolean keyDown(int keycode) {
        if (state.screen() != ArenaGameState.Screen.PLAYING) {
            return false;
        }
        if (movementKey(keycode, true)) {
            enqueueMovement(world.tick());
            return true;
        }
        if (keycode == Input.Keys.SPACE && !fire) {
            fire = true;
            enqueueFire(world.tick());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (movementKey(keycode, false)) {
            long current = world.tick();
            long target = current == lastMovementTarget ? Math.addExact(current, 1) : current;
            enqueueMovement(target);
            return true;
        }
        if (keycode == Input.Keys.SPACE) {
            fire = false;
            return true;
        }
        return false;
    }

    /** Clears held state and source-local sequence only at a world reset boundary. */
    public void reset() {
        sequence = 0;
        lastMovementTarget = -1;
        up = false;
        down = false;
        left = false;
        right = false;
        fire = false;
    }

    private boolean movementKey(int keycode, boolean pressed) {
        switch (keycode) {
            case Input.Keys.W -> up = pressed;
            case Input.Keys.S -> down = pressed;
            case Input.Keys.A -> left = pressed;
            case Input.Keys.D -> right = pressed;
            default -> {
                return false;
            }
        }
        return true;
    }

    private void enqueueMovement(long targetTick) {
        double x = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double y = (up ? 1.0 : 0.0) - (down ? 1.0 : 0.0);
        Vec2 direction = normalized(x, y);
        world.enqueue(new CommandEnvelope(targetTick, KEYBOARD, sequence++,
                new MoveCommand(ArenaWorldFactory.PLAYER_ID, direction)));
        lastMovementTarget = targetTick;
    }

    private void enqueueFire(long targetTick) {
        var snapshot = world.snapshot();
        var player = snapshot.entity(ArenaWorldFactory.PLAYER_ID);
        var enemy = snapshot.entity(ArenaWorldFactory.ENEMY_ID);
        if (player.isEmpty() || enemy.isEmpty()) {
            return;
        }
        Vec2 origin = player.orElseThrow().component(Transform2D.TYPE)
                .orElseThrow().position();
        Vec2 target = enemy.orElseThrow().component(Transform2D.TYPE)
                .orElseThrow().position();
        Vec2 direction = normalized(target.x() - origin.x(), target.y() - origin.y());
        world.enqueue(new CommandEnvelope(targetTick, KEYBOARD, sequence++,
                new AimCommand(ArenaWorldFactory.PLAYER_ID, direction)));
        world.enqueue(new CommandEnvelope(targetTick, KEYBOARD, sequence++,
                new FireCommand(ArenaWorldFactory.PLAYER_ID, origin, direction)));
    }

    private static Vec2 normalized(double x, double y) {
        double length = Math.hypot(x, y);
        return length == 0.0 ? Vec2.ZERO : new Vec2(x / length, y / length);
    }
}
