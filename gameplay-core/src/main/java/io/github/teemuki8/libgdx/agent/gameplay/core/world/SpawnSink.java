package io.github.teemuki8.libgdx.agent.gameplay.core.world;

/** Restricted spawn surface supplied to a world initializer. */
@FunctionalInterface
public interface SpawnSink {
    /** Queues a detached draft for activation. */
    void spawn(EntityDraft draft);
}
