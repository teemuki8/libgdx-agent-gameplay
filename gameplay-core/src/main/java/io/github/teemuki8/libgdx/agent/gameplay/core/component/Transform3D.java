package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Authoritative three-dimensional pose with positive local-axis visual scale. */
public record Transform3D(Vec3 position, QuaternionValue rotation, Vec3 scale) implements Component {
    public static final ComponentType<Transform3D> TYPE =
            new ComponentType<>("transform3d", Transform3D.class);

    /** Validates immutable pose and positive scale. */
    public Transform3D {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
        if (scale.x() <= 0 || scale.y() <= 0 || scale.z() <= 0) {
            throw GameplayException.validation(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-transform3d", "positive scale on every axis", scale.toString(),
                    "Use positive dimensions; collision dimensions belong to the physics adapter.");
        }
    }

    /** Creates a pose with unit scale. */
    public Transform3D(Vec3 position, QuaternionValue rotation) {
        this(position, rotation, Vec3.ONE);
    }
}
