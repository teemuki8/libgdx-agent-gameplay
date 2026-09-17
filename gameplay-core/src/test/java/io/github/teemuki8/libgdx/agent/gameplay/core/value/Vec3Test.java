package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import org.junit.jupiter.api.Test;

final class Vec3Test {
    private static final double EPSILON = 1e-9;

    @Test void arithmeticCombinesComponentWise() {
        Vec3 sum = new Vec3(1, -2, 3).add(new Vec3(.5, 4, -1));
        assertEquals(1.5, sum.x(), EPSILON);
        assertEquals(2, sum.y(), EPSILON);
        assertEquals(2, sum.z(), EPSILON);
        Vec3 difference = new Vec3(1, 2, 3).subtract(new Vec3(3, 1, 1));
        assertEquals(-2, difference.x(), EPSILON);
        assertEquals(1, difference.y(), EPSILON);
        Vec3 scaled = new Vec3(1, -2, 3).scale(2);
        assertEquals(2, scaled.x(), EPSILON);
        assertEquals(-4, scaled.y(), EPSILON);
    }

    @Test void dotAndCrossFollowRightHandedConventions() {
        assertEquals(12, new Vec3(1, 2, 3).dot(new Vec3(4, -5, 6)), EPSILON);
        Vec3 crossed = new Vec3(1, 0, 0).cross(new Vec3(0, 1, 0));
        assertEquals(0, crossed.x(), EPSILON);
        assertEquals(0, crossed.y(), EPSILON);
        assertEquals(1, crossed.z(), EPSILON);
    }

    @Test void lengthAndNormalizationAreStable() {
        assertEquals(5, new Vec3(3, 4, 0).length(), EPSILON);
        Vec3 unit = new Vec3(0, 3, 4).normalized();
        assertEquals(0, unit.x(), EPSILON);
        assertEquals(.6, unit.y(), EPSILON);
        assertEquals(.8, unit.z(), EPSILON);
        assertTrue(Math.abs(unit.length() - 1) <= EPSILON);
        assertEquals(Vec3.ZERO, Vec3.ZERO.normalized());
    }

    @Test void nonFiniteFactorIsRejected() {
        assertThrows(GameplayException.class, () -> Vec3.ONE.scale(Double.NaN));
        assertThrows(GameplayException.class, () -> Vec3.ONE.scale(Double.POSITIVE_INFINITY));
    }
}
