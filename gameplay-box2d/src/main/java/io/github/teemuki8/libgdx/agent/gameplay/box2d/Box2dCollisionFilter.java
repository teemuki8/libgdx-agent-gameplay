package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/**
 * Immutable copied Box2D collision filter without native shape identity.
 *
 * @param categoryBits unsigned 16-bit collision category bits
 * @param maskBits unsigned 16-bit collision mask bits
 * @param groupIndex signed 16-bit collision group; zero applies category/mask filtering,
 *        positive forces same-group collision, and negative prevents it
 */
public record Box2dCollisionFilter(int categoryBits, int maskBits, int groupIndex) {
    /** Validates the bounded values accepted by the gameplay bridge. */
    public Box2dCollisionFilter {
        if (categoryBits < 0 || categoryBits > 0xffff
                || maskBits < 0 || maskBits > 0xffff
                || groupIndex < Short.MIN_VALUE || groupIndex > Short.MAX_VALUE) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "configure-collision-filter",
                    "unsigned 16-bit category/mask and signed 16-bit group",
                    categoryBits + ":" + maskBits + ":" + groupIndex,
                    "Use category and mask values in [0,65535] and group in [-32768,32767].");
        }
    }
}
