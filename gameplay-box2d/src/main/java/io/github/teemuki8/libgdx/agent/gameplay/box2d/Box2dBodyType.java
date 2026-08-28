package io.github.teemuki8.libgdx.agent.gameplay.box2d;

/** Backend-neutral Box2D body authority type. */
public enum Box2dBodyType {
    /** Immovable body. */ STATIC,
    /** Application-velocity-driven body. */ KINEMATIC,
    /** Force-driven simulated body. */ DYNAMIC
}
