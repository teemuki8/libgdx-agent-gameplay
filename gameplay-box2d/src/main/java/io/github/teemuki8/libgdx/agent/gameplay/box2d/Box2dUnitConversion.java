package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable conversion between Box2D metres and gameplay render units. */
public record Box2dUnitConversion(double renderUnitsPerMeter) {
    /** Requires one positive finite scale that remains representable by Box2D floats. */
    public Box2dUnitConversion {
        if (!Double.isFinite(renderUnitsPerMeter) || renderUnitsPerMeter <= 0.0
                || !Float.isFinite((float) renderUnitsPerMeter)) {
            throw failure("positive finite float-representable renderUnitsPerMeter",
                    Double.toString(renderUnitsPerMeter));
        }
    }

    /** Converts one render-unit scalar to physics metres. */
    public double toPhysicsUnits(double renderUnits) {
        return finite(renderUnits / renderUnitsPerMeter);
    }

    /** Converts one physics-metre scalar to render units. */
    public double toRenderUnits(double physicsMeters) {
        return finite(physicsMeters * renderUnitsPerMeter);
    }

    /** Converts an immutable gameplay vector to physics metres. */
    public Vec2 toPhysicsUnits(Vec2 renderUnits) {
        Objects.requireNonNull(renderUnits, "renderUnits");
        return new Vec2(toPhysicsUnits(renderUnits.x()), toPhysicsUnits(renderUnits.y()));
    }

    /** Converts an immutable physics vector to gameplay render units. */
    public Vec2 toRenderUnits(double physicsX, double physicsY) {
        return new Vec2(toRenderUnits(physicsX), toRenderUnits(physicsY));
    }

    float toPhysicsFloat(double renderUnits, String field) {
        double converted = toPhysicsUnits(renderUnits);
        float narrowed = (float) converted;
        if (!Float.isFinite(narrowed)) {
            throw failure("float-representable converted " + field, Double.toString(converted));
        }
        return narrowed;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw failure("finite Box2D unit conversion", Double.toString(value));
        }
        return value;
    }

    private static GameplayException failure(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                "convert-box2d-units",
                expected,
                observed,
                "Use finite world values and one positive bounded unit conversion.");
    }
}
