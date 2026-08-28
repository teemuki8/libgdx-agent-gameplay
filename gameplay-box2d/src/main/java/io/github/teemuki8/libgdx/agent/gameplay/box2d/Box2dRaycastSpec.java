package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable bounded raycast request in render units. */
public record Box2dRaycastSpec(
        Vec2 originRenderUnits,
        Vec2 translationRenderUnits,
        int categoryBits,
        int maskBits,
        int maxHits) {
    /** Validates copied filtering and result bounds. */
    public Box2dRaycastSpec {
        Objects.requireNonNull(originRenderUnits, "originRenderUnits");
        Objects.requireNonNull(translationRenderUnits, "translationRenderUnits");
        if (categoryBits < 0 || categoryBits > 0xffff
                || maskBits < 0 || maskBits > 0xffff) {
            throw new IllegalArgumentException("raycast categoryBits and maskBits must be in [0,65535]");
        }
        if (maxHits < 1 || maxHits > 64) {
            throw new IllegalArgumentException("maxHits must be in [1,64]");
        }
    }
}
