package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.CanonicalComponentWriter;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterSmoothCrouchTest {
    @Test void fractionHeightAndSpeedInterpolateAndReachExactDurationEndpoints() {
        try (var world = floor()) {
            var config = config(0.2);
            var motor = new BulletCharacterMotor(world, config);
            var state = standing();
            for (int tick = 1; tick <= 4; tick++) {
                state = motor.step(state, crouch(), 0.05);
                assertEquals(tick / 4.0, state.crouchFraction(), 1e-12);
                assertEquals(1.8 - 0.8 * tick / 4, config.heightAt(state.crouchFraction()), 1e-12);
                assertEquals(5 * (1 - 0.5 * tick / 4), state.velocity().x(), 1e-6);
                assertTrue(state.crouched());
            }
            assertEquals(1.0, state.crouchFraction());
            for (int tick = 1; tick <= 4; tick++) {
                state = motor.step(state, CharacterIntent.idle(), 0.05);
                assertEquals(1 - tick / 4.0, state.crouchFraction(), 1e-12);
            }
            assertEquals(0.0, state.crouchFraction());
            assertFalse(state.crouched());
        }
    }

    @Test void releaseAndRepressReverseFromCurrentAuthoritativeFraction() {
        try (var world = floor()) {
            var motor = new BulletCharacterMotor(world, config(0.2));
            var state = standing();
            for (int tick = 0; tick < 3; tick++) state = motor.step(state, crouch(), 1.0 / 60);
            assertEquals(0.25, state.crouchFraction(), 1e-12);
            state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertEquals(1.0 / 6, state.crouchFraction(), 1e-12);
            state = motor.step(state, crouch(), 1.0 / 60);
            assertEquals(0.25, state.crouchFraction(), 1e-12);
            for (int tick = 0; tick < 9; tick++) state = motor.step(state, crouch(), 1.0 / 60);
            assertEquals(1.0, state.crouchFraction());
            for (int tick = 0; tick < 12; tick++) state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertEquals(0.0, state.crouchFraction());
        }
    }

    @Test void growingCapsuleStopsAtClearanceThenFinishesAfterLeavingCeiling() {
        try (var world = floor()) {
            world.addBox(EntityId.of("ceiling"), pose(0, 1.5, 0), new Vec3(2, 0.2, 2));
            var config = config(0.2);
            var motor = new BulletCharacterMotor(world, config);
            var state = new CharacterState(new Vec3(0, 0.002, 0), Vec3.ZERO, true, true);
            for (int tick = 0; tick < 60; tick++) {
                state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
                assertTrue(state.feet().y() + config.heightAt(state.crouchFraction()) <= 1.301,
                        "proposed capsule must fit under the roof without moving feet through floor: " + state);
                assertTrue(state.feet().y() >= -0.001);
            }
            assertTrue(state.crouchFraction() > 0 && state.crouchFraction() < 1);
            double blocked = state.crouchFraction();
            state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertEquals(blocked, state.crouchFraction());
            for (int tick = 0; tick < 120; tick++) state = motor.step(state,
                    new CharacterIntent(new Vec3(1, 0, 0), false, false), 1.0 / 60);
            assertTrue(state.feet().x() > 3);
            assertEquals(0, state.crouchFraction());
        }
    }

    @Test void compatibilityConstructorsKeepInstantEndpoints() {
        var config = new CharacterConfig(0.3, 1.8, 1, 5, 25, 30, 20, 7, 0.3, Math.toRadians(45), 0.5);
        assertEquals(0, config.crouchTransitionSeconds());
        try (var world = floor()) {
            var motor = new BulletCharacterMotor(world, config);
            var state = motor.step(standing(), crouch(), 1.0 / 60);
            assertEquals(1, state.crouchFraction());
            state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertEquals(0, state.crouchFraction());
        }
        assertEquals(1, new CharacterStance(true, true).crouchFraction());
        assertEquals(0, new CharacterStance(true, false).crouchFraction());
        assertEquals(0.18, CharacterConfig.defaults().crouchTransitionSeconds());
    }

    @Test void rejectsInvalidFractionsAndDurationsAndCanonicalEncodingIncludesFraction() {
        for (double fraction : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new CharacterState(Vec3.ZERO, Vec3.ZERO, true, fraction));
            assertThrows(IllegalArgumentException.class, () -> new CharacterStance(true, fraction));
            assertThrows(IllegalArgumentException.class, () -> config(0.2).heightAt(fraction));
        }
        for (double seconds : new double[]{-0.1, 5.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> config(seconds));
        }
        List<Object> fields = new ArrayList<>();
        var writer = new CanonicalComponentWriter() {
            @Override public void bool(boolean value) { fields.add(value); }
            @Override public void integer(int value) { fields.add(value); }
            @Override public void longValue(long value) { fields.add(value); }
            @Override public void decimal(double value) { fields.add(value); }
            @Override public void text(String value) { fields.add(value); }
        };
        CharacterStance.CODEC.encode(new CharacterStance(true, 0.25), writer);
        assertEquals(List.of(true, true, 0.25), fields);
    }

    private static CharacterConfig config(double seconds) {
        return new CharacterConfig(0.3, 1.8, 1.0, 5, 100, 100, 20, 7, 0.3, Math.toRadians(45), 0.5, seconds);
    }
    private static CharacterState standing() {
        return new CharacterState(new Vec3(0, 0.002, 0), Vec3.ZERO, true, false);
    }
    private static CharacterIntent crouch() { return new CharacterIntent(new Vec3(1, 0, 0), false, true); }
    private static GameplayBulletWorld floor() {
        var world = new GameplayBulletWorld(8);
        world.addBox(EntityId.of("floor"), pose(0, -0.5, 0), new Vec3(20, 0.5, 20));
        return world;
    }
    private static Transform3D pose(double x, double y, double z) {
        return new Transform3D(new Vec3(x, y, z), new QuaternionValue(0, 0, 0, 1));
    }
}
