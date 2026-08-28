package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Declarative collider geometry, offset, sensor state, and 16-bit filters. */
public record Collider(
        Shape shape,
        Vec2 size,
        Vec2 offset,
        boolean sensor,
        int categoryBits,
        int maskBits) implements Component {
    public static final ComponentType<Collider> TYPE =
            new ComponentType<>("collider", Collider.class);

    /** Supported backend-neutral collider geometries. */
    public enum Shape {
        BOX,
        CIRCLE,
        CAPSULE
    }

    /** Validates positive geometry and unsigned 16-bit filters. */
    public Collider {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(offset, "offset");
        if (size.x() <= 0.0 || size.y() <= 0.0
                || categoryBits < 0 || categoryBits > 0xffff
                || maskBits < 0 || maskBits > 0xffff) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-collider",
                    "positive size and category/mask in [0,65535]",
                    "size=" + size + ",category=" + categoryBits + ",mask=" + maskBits,
                    "Use positive collider dimensions and unsigned 16-bit filter values.");
        }
        if (shape == Shape.CAPSULE && Double.compare(size.x(), size.y()) == 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-collider",
                    "capsule with one dimension strictly larger than the other",
                    "size=" + size,
                    "Use CIRCLE for equal dimensions or lengthen one capsule axis.");
        }
    }
}
