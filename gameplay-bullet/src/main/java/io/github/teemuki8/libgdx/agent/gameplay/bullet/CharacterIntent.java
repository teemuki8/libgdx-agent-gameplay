package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Command-derived world-space horizontal intent; jump is an edge, crouch is held. */
public record CharacterIntent(Vec3 movement, boolean jump, boolean crouch) {
    public CharacterIntent {
        Objects.requireNonNull(movement, "movement");
        if (movement.y() != 0 || Math.hypot(movement.x(), movement.z()) > 1.000001) {
            throw new IllegalArgumentException("horizontal movement length must not exceed one");
        }
    }
    public static CharacterIntent idle() { return new CharacterIntent(Vec3.ZERO, false, false); }
}
