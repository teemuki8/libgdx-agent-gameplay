package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable copied specification for a bridge-owned revolute joint. */
public record Box2dRevoluteJointSpec(
        Box2dJointId id,
        EntityId first,
        EntityId second,
        Vec2 anchorRenderUnits,
        double lowerAngleRadians,
        double upperAngleRadians,
        boolean collideConnected) {
    /** Validates endpoints, copied anchor, and finite ordered angular limits. */
    public Box2dRevoluteJointSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(anchorRenderUnits, "anchorRenderUnits");
        if (first.equals(second)) {
            throw invalid("distinct joint endpoints", first.value());
        }
        if (!Double.isFinite(lowerAngleRadians) || !Double.isFinite(upperAngleRadians)
                || lowerAngleRadians > upperAngleRadians) {
            throw invalid("finite lowerAngleRadians <= upperAngleRadians",
                    lowerAngleRadians + ":" + upperAngleRadians);
        }
    }

    private static GameplayException invalid(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                "create-revolute-joint-spec", expected, observed,
                "Supply distinct mapped endpoints and finite ordered angular limits.");
    }
}
