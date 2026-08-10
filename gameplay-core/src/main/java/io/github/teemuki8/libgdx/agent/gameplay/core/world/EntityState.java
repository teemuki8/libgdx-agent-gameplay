package io.github.teemuki8.libgdx.agent.gameplay.core.world;

/** Explicit entity lifecycle states used in snapshots and diagnostics. */
public enum EntityState {
    QUEUED,
    ACTIVE,
    LOGICALLY_REMOVED,
    DISPOSED
}
