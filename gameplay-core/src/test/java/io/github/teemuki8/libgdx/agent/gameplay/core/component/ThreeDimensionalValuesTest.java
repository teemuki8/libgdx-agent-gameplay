package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.replay.CanonicalWorldEncoder;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ThreeDimensionalValuesTest {
    @Test
    void everyAuthoritativeThreeDimensionalFieldAffectsReplayDigest() {
        var encoder = CanonicalWorldEncoder.defaults();
        var identity = new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY);
        var baseline = encoder.digest(snapshot(identity, new Velocity3D(Vec3.ZERO), new Aim3D(0, 0)));
        for (Transform3D changed : List.of(
                new Transform3D(new Vec3(0, 0, 1), QuaternionValue.IDENTITY),
                new Transform3D(Vec3.ZERO, new QuaternionValue(0, 1, 0, 0)),
                new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY, new Vec3(1, 1, 2)))) {
            assertNotEquals(baseline, encoder.digest(snapshot(changed, new Velocity3D(Vec3.ZERO), new Aim3D(0, 0))));
        }
        assertNotEquals(baseline, encoder.digest(snapshot(identity, new Velocity3D(new Vec3(0, 0, 1)), new Aim3D(0, 0))));
        assertNotEquals(baseline, encoder.digest(snapshot(identity, new Velocity3D(Vec3.ZERO), new Aim3D(0, 0.5))));
        assertNotEquals(baseline, encoder.digest(snapshot(identity, new Velocity3D(Vec3.ZERO), new Aim3D(0.5, 0))));
        assertEquals(baseline, encoder.digest(snapshot(
                new Transform3D(new Vec3(-0.0, 0, -0.0), QuaternionValue.IDENTITY),
                new Velocity3D(Vec3.ZERO), new Aim3D(-0.0, -0.0))));
        assertThrows(GameplayException.class, () -> new CanonicalWorldEncoder(32).encode(
                snapshot(identity, new Velocity3D(Vec3.ZERO), new Aim3D(0, 0))));
    }

    @Test
    void viewDirectionsUseYUpAndNegativeZForward() {
        assertEquals(new Vec3(0, 0, -1), new Aim3D(0, 0).direction());
        assertEquals(1, new Aim3D(Math.PI / 2, 0).direction().x(), 1e-12);
        assertEquals(1, new Aim3D(0, Math.PI / 2).direction().y(), 1e-12);
        Vec3 oblique = new Aim3D(0.8, -0.3).direction();
        assertEquals(1, oblique.x() * oblique.x() + oblique.y() * oblique.y()
                + oblique.z() * oblique.z(), 1e-12);
    }

    @Test
    void valuesRejectNonFiniteDegenerateAndOutOfRangeInputs() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(GameplayException.class, () -> new Vec3(0, bad, 0));
            assertThrows(GameplayException.class, () -> new QuaternionValue(0, 0, 0, bad));
            assertThrows(GameplayException.class, () -> new Aim3D(bad, 0));
            assertThrows(GameplayException.class, () -> new Aim3D(0, bad));
        }
        assertThrows(GameplayException.class, () -> new QuaternionValue(0, 0, 0, 0));
        assertThrows(GameplayException.class, () -> new QuaternionValue(0, 0, 0, 2));
        assertThrows(GameplayException.class, () -> new Aim3D(Math.nextUp(Math.PI), 0));
        assertThrows(GameplayException.class, () -> new Aim3D(0, Math.nextUp(Math.PI / 2)));
        assertThrows(GameplayException.class, () -> new Transform3D(Vec3.ZERO,
                QuaternionValue.IDENTITY, new Vec3(-1, 1, 1)));
    }

    private static WorldSnapshot snapshot(Transform3D transform, Velocity3D velocity, Aim3D aim) {
        return new WorldSnapshot(1, List.of(new EntitySnapshot(EntityId.of("player"),
                EntityState.ACTIVE, Map.of(Transform3D.TYPE, transform, Velocity3D.TYPE, velocity, Aim3D.TYPE, aim))));
    }
}
