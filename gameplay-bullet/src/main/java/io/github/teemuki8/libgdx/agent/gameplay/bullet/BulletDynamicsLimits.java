package io.github.teemuki8.libgdx.agent.gameplay.bullet;

/** Hard allocation ceilings chosen by the consuming application. */
public record BulletDynamicsLimits(int maxBodies, int maxConstraints) {
    /** Validates the application budget against library hard bounds. */
    public BulletDynamicsLimits {
        if (maxBodies < 1 || maxBodies > 4096 || maxConstraints < 0 || maxConstraints > 8192) {
            throw new IllegalArgumentException("Bullet dynamics limits require 1..4096 bodies and 0..8192 constraints");
        }
    }
}
