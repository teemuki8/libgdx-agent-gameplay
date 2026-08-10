package io.github.teemuki8.libgdx.agent.gameplay.core.world;

/** Precompiled deterministic initial-world definition replayed after reset. */
@FunctionalInterface
public interface WorldInitializer {
    /** Queues the world's initial drafts through the restricted sink. */
    void initialize(SpawnSink sink);
}
