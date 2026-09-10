package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;

/**
 * View intent in radians: Y up, zero yaw faces negative Z, positive yaw turns right,
 * and positive pitch looks up. Yaw is bounded to [-pi,pi], pitch to [-pi/2,pi/2].
 * This view convention is independent of the body's right-handed quaternion rotation.
 */
public record Aim3D(double yawRadians, double pitchRadians) implements Component {
    public static final ComponentType<Aim3D> TYPE = new ComponentType<>("aim3d", Aim3D.class);

    /** Requires finite angles in the documented closed ranges. */
    public Aim3D {
        if (!Double.isFinite(yawRadians) || Math.abs(yawRadians) > Math.PI
                || !Double.isFinite(pitchRadians) || Math.abs(pitchRadians) > Math.PI / 2) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-aim3d", "yaw in [-pi,pi] and pitch in [-pi/2,pi/2]",
                    yawRadians + "," + pitchRadians,
                    "Wrap yaw and clamp pitch in the production input adapter.");
        }
    }

    /** Returns the unit world-space direction of this view intent. */
    public Vec3 direction() {
        double horizontal = StrictMath.cos(pitchRadians);
        return new Vec3(StrictMath.sin(yawRadians) * horizontal,
                StrictMath.sin(pitchRadians), -StrictMath.cos(yawRadians) * horizontal);
    }
}
