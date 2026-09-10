package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Immutable domain input/output. Position is at the feet, never the capsule centre. */
public record CharacterState(Vec3 feet, Vec3 velocity, boolean grounded, double crouchFraction) {
    public CharacterState {
        CharacterConfig.requireFraction(crouchFraction);
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(velocity, "velocity");
    }
    /** Compatibility constructor maps the old binary posture to exact endpoints. */
    public CharacterState(Vec3 feet, Vec3 velocity, boolean grounded, boolean crouched) {
        this(feet, velocity, grounded, crouched ? 1.0 : 0.0);
    }
    /** Any partial or complete crouch, including blocked standing, is crouched. */
    public boolean crouched() { return crouchFraction > 0; }
}
