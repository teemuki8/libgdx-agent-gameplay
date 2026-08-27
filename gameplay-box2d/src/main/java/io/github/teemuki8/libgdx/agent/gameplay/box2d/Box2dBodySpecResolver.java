package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;

/** Resolves a complete copied Box2D body specification at entity activation. */
@FunctionalInterface
public interface Box2dBodySpecResolver {
    /** Returns the immutable specification retained for the native body lifetime. */
    Box2dBodySpec resolve(EntityView entity);
}
