package io.github.teemuki8.libgdx.agent.gameplay.bullet;

/** Bounded kinematic capsule tuning, in metres and seconds. */
public record CharacterConfig(double radius, double standingHeight, double crouchedHeight,
        double speed, double acceleration, double braking, double gravity, double jumpSpeed,
        double stepHeight, double maxSlopeRadians) {
    public CharacterConfig {
        for (double value : new double[]{radius, standingHeight, crouchedHeight, speed,
                acceleration, braking, gravity, jumpSpeed, stepHeight, maxSlopeRadians}) {
            if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("character tuning");
        }
        if (radius < 0.01 || crouchedHeight < 2 * radius || standingHeight < crouchedHeight
                || maxSlopeRadians >= Math.PI / 2 || acceleration == 0 || braking == 0) {
            throw new IllegalArgumentException("invalid character dimensions or slope");
        }
    }
    /** Human-scale default capsule and responsive authored movement. */
    public static CharacterConfig defaults() {
        return new CharacterConfig(0.3, 1.8, 1.0, 5, 25, 30, 20, 7, 0.3, Math.toRadians(45));
    }
}
