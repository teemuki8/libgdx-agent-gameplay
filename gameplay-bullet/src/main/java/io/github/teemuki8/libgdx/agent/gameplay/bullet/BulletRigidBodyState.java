package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;

/** Copied immutable rigid-body facts after the latest mutation or step. */
public record BulletRigidBodyState(Vec3 position, QuaternionValue rotation,
        Vec3 linearVelocity, Vec3 angularVelocity) { }
