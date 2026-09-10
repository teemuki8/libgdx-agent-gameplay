package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Immutable domain input/output. Position is at the feet, never the capsule centre. */
public record CharacterState(Vec3 feet, Vec3 velocity, boolean grounded, boolean crouched) {
    public CharacterState {
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(velocity, "velocity");
    }
}
