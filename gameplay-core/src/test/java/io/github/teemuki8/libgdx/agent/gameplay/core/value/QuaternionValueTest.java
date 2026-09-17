package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import org.junit.jupiter.api.Test;

final class QuaternionValueTest {
    private static final double EPSILON = 1e-9;

    @Test void identityRotationLeavesVectorsAlone() {
        Vec3 rotated = QuaternionValue.IDENTITY.rotate(new Vec3(.3, -.7, 2));
        assertEquals(.3, rotated.x(), EPSILON);
        assertEquals(-.7, rotated.y(), EPSILON);
        assertEquals(2, rotated.z(), EPSILON);
    }

    @Test void axisAngleRotationMovesTheBasisAsExpected() {
        QuaternionValue quarterTurnY = QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Math.PI / 2);
        Vec3 x = quarterTurnY.rotate(new Vec3(1, 0, 0));
        Vec3 z = quarterTurnY.rotate(new Vec3(0, 0, 1));
        // Right-handed positive rotation about +Y takes +X to -Z and +Z to +X.
        assertEquals(0, x.x(), EPSILON);
        assertEquals(0, x.y(), EPSILON);
        assertEquals(-1, x.z(), EPSILON);
        assertEquals(1, z.x(), EPSILON);
        assertEquals(0, z.y(), EPSILON);
        assertEquals(0, z.z(), EPSILON);
    }

    @Test void axisAngleNormalizesTheAxisAndAcceptsOffAxisLengths() {
        QuaternionValue normalized = QuaternionValue.fromAxisAngle(new Vec3(0, 3, 0), Math.PI / 2);
        Vec3 x = normalized.rotate(new Vec3(1, 0, 0));
        assertEquals(0, x.x(), EPSILON);
        assertEquals(-1, x.z(), EPSILON);
    }

    @Test void compositionRotatesLikeNestedApplication() {
        QuaternionValue yaw = QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Math.PI / 2);
        QuaternionValue pitch = QuaternionValue.fromAxisAngle(new Vec3(1, 0, 0), Math.PI / 3);
        Vec3 vector = new Vec3(.4, -.2, .9);
        Vec3 composed = yaw.multiply(pitch).rotate(vector);
        Vec3 nested = yaw.rotate(pitch.rotate(vector));
        assertEquals(composed.x(), nested.x(), EPSILON);
        assertEquals(composed.y(), nested.y(), EPSILON);
        assertEquals(composed.z(), nested.z(), EPSILON);
    }

    @Test void quarterTurnsComposeIntoAHalfTurn() {
        QuaternionValue quarter = QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Math.PI / 2);
        QuaternionValue half = quarter.multiply(quarter);
        Vec3 x = half.rotate(new Vec3(1, 0, 0));
        assertEquals(-1, x.x(), EPSILON);
        assertEquals(0, x.z(), EPSILON);
    }

    @Test void invalidAxisAndAngleAreRejected() {
        assertThrows(GameplayException.class, () -> QuaternionValue.fromAxisAngle(Vec3.ZERO, 1));
        assertThrows(GameplayException.class,
                () -> QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Double.NaN));
        assertThrows(GameplayException.class,
                () -> QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Double.POSITIVE_INFINITY));
    }

    @Test void productsStayUnitWithinTheDocumentedTolerance() {
        QuaternionValue left = QuaternionValue.fromAxisAngle(new Vec3(1, 0, 0), .4);
        QuaternionValue right = QuaternionValue.fromAxisAngle(new Vec3(0, 0, 1), -1.2);
        QuaternionValue product = left.multiply(right);
        double lengthSquared = product.x() * product.x() + product.y() * product.y()
                + product.z() * product.z() + product.w() * product.w();
        assertTrue(Math.abs(lengthSquared - 1) <= 1e-6);
    }
}
