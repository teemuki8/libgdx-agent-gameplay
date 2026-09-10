package io.github.teemuki8.libgdx.agent.gameplay.bullet;

/** Bounded kinematic capsule tuning, in metres and seconds. Posture transitions span [0,5] seconds; zero is instant. */
public record CharacterConfig(double radius, double standingHeight, double crouchedHeight,
        double speed, double acceleration, double braking, double gravity, double jumpSpeed,
        double stepHeight, double maxSlopeRadians, double crouchSpeedScale, double crouchTransitionSeconds) {
    public CharacterConfig {
        for (double value : new double[]{radius, standingHeight, crouchedHeight, speed,
                acceleration, braking, gravity, jumpSpeed, stepHeight, maxSlopeRadians, crouchSpeedScale, crouchTransitionSeconds}) {
            if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("character tuning");
        }
        if (radius < 0.01 || crouchedHeight < 2 * radius || standingHeight < crouchedHeight
                || crouchSpeedScale > 1 || crouchTransitionSeconds > 5 || maxSlopeRadians >= Math.PI / 2 || acceleration == 0 || braking == 0) {
            throw new IllegalArgumentException("invalid character dimensions or slope");
        }
    }
    /** Compatibility constructor retains instant posture changes. */
    public CharacterConfig(double radius, double standingHeight, double crouchedHeight,
            double speed, double acceleration, double braking, double gravity, double jumpSpeed,
            double stepHeight, double maxSlopeRadians, double crouchSpeedScale) {
        this(radius, standingHeight, crouchedHeight, speed, acceleration, braking, gravity, jumpSpeed,
                stepHeight, maxSlopeRadians, crouchSpeedScale, 0);
    }
    /** Capsule height at an authoritative posture fraction in [0,1]. */
    public double heightAt(double crouchFraction) {
        requireFraction(crouchFraction);
        if (crouchFraction == 1) return crouchedHeight;
        return standingHeight + (crouchedHeight - standingHeight) * crouchFraction;
    }
    /** Movement speed multiplier at the same authoritative posture fraction. */
    public double speedScaleAt(double crouchFraction) {
        requireFraction(crouchFraction);
        if (crouchFraction == 1) return crouchSpeedScale;
        return 1 + (crouchSpeedScale - 1) * crouchFraction;
    }
    static void requireFraction(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("crouch fraction: [0,1]");
        }
    }
    /** Compatibility constructor retains the original half-speed crouch. */
    public CharacterConfig(double radius, double standingHeight, double crouchedHeight,
            double speed, double acceleration, double braking, double gravity, double jumpSpeed,
            double stepHeight, double maxSlopeRadians) {
        this(radius, standingHeight, crouchedHeight, speed, acceleration, braking, gravity, jumpSpeed,
                stepHeight, maxSlopeRadians, 0.5);
    }
    /** Human-scale default capsule and responsive authored movement. */
    public static CharacterConfig defaults() {
        return new CharacterConfig(0.3, 1.8, 1.0, 5, 25, 30, 20, 7, 0.3, Math.toRadians(45), 0.5, 0.18);
    }
}
