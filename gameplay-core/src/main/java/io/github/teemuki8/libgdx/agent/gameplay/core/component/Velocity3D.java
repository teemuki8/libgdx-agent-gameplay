package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Objects;

/** Authoritative linear velocity in world units per second. */
public record Velocity3D(Vec3 linear) implements Component {
    public static final ComponentType<Velocity3D> TYPE =
            new ComponentType<>("velocity3d", Velocity3D.class);

    /** Requires a finite immutable vector. */
    public Velocity3D {
        Objects.requireNonNull(linear, "linear");
    }
}
