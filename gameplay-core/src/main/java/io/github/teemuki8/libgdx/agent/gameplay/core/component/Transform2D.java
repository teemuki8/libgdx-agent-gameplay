package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Authoritative position, rotation, visual size, and normalized pivot. */
public record Transform2D(Vec2 position, double rotationRadians, Vec2 size, Vec2 pivot)
        implements Component {
    public static final ComponentType<Transform2D> TYPE =
            new ComponentType<>("transform", Transform2D.class);

    /** Validates finite pose, positive size, and normalized pivot. */
    public Transform2D {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(pivot, "pivot");
        if (!Double.isFinite(rotationRadians) || size.x() <= 0.0 || size.y() <= 0.0
                || pivot.x() < 0.0 || pivot.x() > 1.0
                || pivot.y() < 0.0 || pivot.y() > 1.0) {
            throw invalid("finite rotation, positive size, and pivot in [0,1]", toStringValue(
                    rotationRadians, size, pivot));
        }
    }

    private static GameplayException invalid(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                "create-transform",
                expected,
                observed,
                "Use finite pose values, positive dimensions, and a normalized pivot.");
    }

    private static String toStringValue(double rotation, Vec2 size, Vec2 pivot) {
        return "rotation=" + rotation + ",size=" + size + ",pivot=" + pivot;
    }
}
