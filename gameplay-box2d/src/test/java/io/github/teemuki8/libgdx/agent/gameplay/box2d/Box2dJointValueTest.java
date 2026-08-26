package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class Box2dJointValueTest {
    private static final EntityId FIRST = EntityId.of("first");
    private static final EntityId SECOND = EntityId.of("second");

    @Test
    void jointIdUsesBoundedSemanticIdentifierRulesAndStableOrdering() {
        assertEquals("hip-left", Box2dJointId.of("hip-left").value());
        assertTrue(Box2dJointId.of("hip-left").compareTo(Box2dJointId.of("hip-right")) < 0);
        assertFailure(GameplayDiagnosticCode.INVALID_IDENTIFIER,
                () -> Box2dJointId.of("not an identifier"));
        assertFailure(GameplayDiagnosticCode.INVALID_IDENTIFIER,
                () -> Box2dJointId.of("a".repeat(257)));
    }

    @Test
    void revoluteJointSpecCopiesValidatedEndpointsAnchorAndLimits() {
        Box2dRevoluteJointSpec spec = new Box2dRevoluteJointSpec(
                Box2dJointId.of("hip"), FIRST, SECOND, new Vec2(32, 48),
                -0.5, 0.75, false);

        assertEquals(new Vec2(32, 48), spec.anchorRenderUnits());
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointSpec(
                        Box2dJointId.of("self"), FIRST, FIRST, Vec2.ZERO, -1, 1, false));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointSpec(
                        Box2dJointId.of("reversed"), FIRST, SECOND,
                        Vec2.ZERO, 1, -1, false));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointSpec(
                        Box2dJointId.of("infinite"), FIRST, SECOND, Vec2.ZERO,
                        Double.NEGATIVE_INFINITY, 1, false));
    }

    @Test
    void revoluteMotorRequiresFiniteSpeedAndValidTorque() {
        assertEquals(12.0, new Box2dRevoluteMotor(true, -4.0, 12.0)
                .maximumTorqueNewtonMetres());
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteMotor(true, 1, 0));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteMotor(false, 1, -1));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteMotor(false, Double.NaN, 0));
    }

    @Test
    void revoluteJointStateContainsOnlyValidatedCopiedValues() {
        Box2dRevoluteJointState state = new Box2dRevoluteJointState(
                Box2dJointId.of("shoulder"), FIRST, SECOND,
                0.25, -2.0, true, 3.0, 15.0);

        assertEquals(FIRST, state.first());
        assertEquals(SECOND, state.second());
        assertEquals(0.25, state.angleRadians());
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointState(
                        Box2dJointId.of("invalid-state"), FIRST, SECOND,
                        Double.NaN, 0, false, 0, 0));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointState(
                        Box2dJointId.of("invalid-torque"), FIRST, SECOND,
                        0, 0, false, 0, -1));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointState(
                        Box2dJointId.of("self-state"), FIRST, FIRST,
                        0, 0, false, 0, 0));
        assertFailure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                () -> new Box2dRevoluteJointState(
                        Box2dJointId.of("enabled-zero-torque"), FIRST, SECOND,
                        0, 0, true, 0, 0));
    }

    private static void assertFailure(GameplayDiagnosticCode code, Executable executable) {
        assertEquals(code, assertThrows(GameplayException.class, executable).code());
    }

}
