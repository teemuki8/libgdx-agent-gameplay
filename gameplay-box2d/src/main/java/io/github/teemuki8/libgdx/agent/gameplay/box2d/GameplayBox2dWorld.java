package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.structs.b2WorldDef;
import com.badlogic.gdx.box2d.structs.b2WorldId;
import java.util.Objects;

/** Application-owned opaque Box2D 3 world. */
public final class GameplayBox2dWorld implements AutoCloseable {
    private final Thread ownerThread;
    private final Box2dWorldSpec spec;
    private final b2WorldId worldId;
    private boolean stepping;
    private boolean closed;

    private GameplayBox2dWorld(Box2dWorldSpec spec, b2WorldId worldId) {
        ownerThread = Thread.currentThread();
        this.spec = spec;
        this.worldId = worldId;
    }

    /** Creates a zero-worker world on the calling owner thread. */
    public static GameplayBox2dWorld create(Box2dWorldSpec spec) {
        Box2d.initialize();
        Box2dWorldSpec checked = Objects.requireNonNull(spec, "spec");
        b2WorldDef definition = Box2d.b2DefaultWorldDef();
        definition.gravity().x(finiteFloat(checked.gravityMetresPerSecondSquared().x()));
        definition.gravity().y(finiteFloat(checked.gravityMetresPerSecondSquared().y()));
        definition.workerCount(0);
        definition.hitEventThreshold(finiteFloat(
                checked.hitEventThresholdMetresPerSecond()));
        return new GameplayBox2dWorld(checked,
                Box2d.b2CreateWorld(definition.asPointer()));
    }

    /** Reports whether native world destruction has completed. */
    public boolean isClosed() {
        requireOwner();
        return closed;
    }

    Box2dWorldSpec spec() {
        requireOwnerOpen();
        return spec;
    }

    b2WorldId id() {
        requireOwnerOpen();
        return worldId;
    }

    void step(float seconds) {
        requireOwnerOpen();
        if (stepping) {
            throw new IllegalStateException("Box2D world step is already active");
        }
        stepping = true;
        try {
            Box2d.b2World_Step(worldId, seconds, spec.subStepCount());
        } finally {
            stepping = false;
        }
    }

    void requireUnlocked() {
        requireOwnerOpen();
        if (stepping) {
            throw new IllegalStateException("Box2D world is locked during step callbacks");
        }
    }

    void requireOwnerOpen() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("GameplayBox2dWorld is closed");
        }
        if (!Box2d.b2World_IsValid(worldId)) {
            throw new IllegalStateException("GameplayBox2dWorld native ID is stale");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("GameplayBox2dWorld owner-thread violation");
        }
    }

    /** Destroys the application-owned world; repeated close is harmless. */
    @Override public void close() {
        requireOwner();
        if (closed) {
            return;
        }
        requireUnlocked();
        Box2d.b2DestroyWorld(worldId);
        closed = true;
    }

    private static float finiteFloat(double value) {
        float narrowed = (float) value;
        if (!Double.isFinite(value) || !Float.isFinite(narrowed)) {
            throw new IllegalArgumentException("world scalar must be finite and float-representable");
        }
        return narrowed;
    }
}
