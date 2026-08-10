package io.github.teemuki8.libgdx.agent.gameplay.core.system;

/** Stable V1 fixed-tick phase catalog in execution order. */
public enum SystemPhase {
    INPUT,
    PRE_PHYSICS,
    PHYSICS,
    POST_PHYSICS,
    GAMEPLAY,
    ANIMATION,
    RENDER_PREP,
    RUNTIME_CAPTURE
}
