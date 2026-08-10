package io.github.teemuki8.libgdx.agent.gameplay.core.system;

/** One deterministic fixed-tick gameplay operation. */
public interface GameSystem {
    /** Returns the immutable scheduling identity. */
    SystemDescriptor descriptor();

    /** Executes once in the compiled slot for the current tick. */
    void update(SystemContext context);
}
