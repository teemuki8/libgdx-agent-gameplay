package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import com.badlogic.gdx.math.Vector3;
import java.util.Objects;

/** Stateless fixed-tick capsule motor. It never retains gameplay position or polls production input. */
public final class BulletCharacterMotor {
    private static final float SKIN = 0.002f;
    private final GameplayBulletWorld world;
    private final CharacterConfig config;
    public BulletCharacterMotor(GameplayBulletWorld world, CharacterConfig config) {
        this.world = Objects.requireNonNull(world, "world");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Resolves one bounded tick, with at most six slide and six depenetration iterations. */
    public CharacterState step(CharacterState state, CharacterIntent intent, double seconds) {
        world.requireOpen();
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 0.1) throw new IllegalArgumentException("tick: (0,0.1]");
        boolean crouched = intent.crouch();
        Vector3 feet = GameplayBulletWorld.vector(state.feet());
        if (state.crouched() && !crouched && overlaps(feet, config.standingHeight())) crouched = true;
        double height = crouched ? config.crouchedHeight() : config.standingHeight();
        for (int i = 0; i < 6; i++) {
            var correction = GameplayBulletWorld.vector(world.capsulePenetration(
                    GameplayBulletWorld.copy(centre(feet, height)), config.radius(), height));
            if (correction.isZero(0.00001f)) break;
            feet.add(correction);
        }
        if (overlaps(feet, height)) throw new IllegalStateException("capsule start overlap exceeds six recovery iterations");
        Vector3 velocity = GameplayBulletWorld.vector(state.velocity());
        Vector3 desired = GameplayBulletWorld.vector(intent.movement()).scl((float) (config.speed() * (crouched ? config.crouchSpeedScale() : 1)));
        Vector3 horizontal = new Vector3(velocity.x, 0, velocity.z);
        Vector3 change = desired.sub(horizontal);
        double rate = intent.movement().equals(io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3.ZERO)
                ? config.braking() : config.acceleration();
        change.limit((float) (rate * seconds));
        horizontal.add(change);
        velocity.x = horizontal.x;
        velocity.z = horizontal.z;
        boolean grounded = ground(feet, height, 0.03) != null && velocity.y <= 0;
        if (grounded) velocity.y = 0;
        if (grounded && intent.jump()) {
            velocity.y = (float) config.jumpSpeed();
            grounded = false;
        }
        velocity.y -= (float) (config.gravity() * seconds);
        Vector3 horizontalDelta = new Vector3(velocity.x, 0, velocity.z).scl((float) seconds);
        Vector3 moved = slide(feet, horizontalDelta, height);
        if (grounded && config.stepHeight() > 0 && new Vector3(moved).sub(feet).len2() + 0.00001 < horizontalDelta.len2()) {
            Vector3 raised = slide(feet, new Vector3(0, (float) config.stepHeight(), 0), height);
            if (raised.y - feet.y >= config.stepHeight() - SKIN * 2) {
                Vector3 across = slide(raised, horizontalDelta, height);
                BulletHit down = ground(across, height, config.stepHeight() + 0.03);
                if (down != null && new Vector3(across.x - feet.x, 0, across.z - feet.z).len2()
                        > new Vector3(moved.x - feet.x, 0, moved.z - feet.z).len2()) {
                    across.y -= (float) ((config.stepHeight() + 0.03) * down.fraction());
                    across.y += SKIN;
                    moved = across;
                }
            }
        }
        Vector3 afterVertical = slide(moved, new Vector3(0, velocity.y * (float) seconds, 0), height);
        if (Math.abs(afterVertical.y - moved.y) + SKIN < Math.abs(velocity.y * seconds)) velocity.y = 0;
        BulletHit floor = velocity.y <= 0 ? ground(afterVertical, height, grounded ? config.stepHeight() + 0.03 : 0.03) : null;
        grounded = floor != null;
        if (grounded) {
            double distance = state.grounded() ? config.stepHeight() + 0.03 : 0.03;
            // Repeat with the same distance used above so the fraction has an explicit unit conversion.
            double probe = (state.grounded() && !intent.jump()) ? distance : 0.03;
            BulletHit snap = ground(afterVertical, height, probe);
            if (snap != null) afterVertical.y -= (float) Math.max(0, probe * snap.fraction() - SKIN);
            velocity.y = 0;
        }
        return new CharacterState(GameplayBulletWorld.copy(afterVertical), GameplayBulletWorld.copy(velocity), grounded, crouched);
    }

    private Vector3 slide(Vector3 feet, Vector3 delta, double height) {
        Vector3 result = new Vector3(feet);
        Vector3 remaining = new Vector3(delta);
        for (int i = 0; i < 6 && remaining.len2() > 1e-10; i++) {
            Vector3 start = centre(result, height);
            var hit = world.sweepCapsule(GameplayBulletWorld.copy(start), GameplayBulletWorld.copy(new Vector3(start).add(remaining)),
                    config.radius(), height);
            if (hit.isEmpty()) return result.add(remaining);
            float fraction = (float) Math.max(0, hit.get().fraction() - SKIN / remaining.len());
            result.mulAdd(remaining, fraction);
            remaining.scl(1 - fraction);
            Vector3 normal = GameplayBulletWorld.vector(hit.get().normal());
            if (delta.y == 0 && normal.y > 0 && normal.y < Math.cos(config.maxSlopeRadians())) {
                normal.y = 0;
                normal.nor();
            }
            remaining.mulAdd(normal, -Math.min(0, remaining.dot(normal)));
        }
        return result;
    }
    private BulletHit ground(Vector3 feet, double height, double distance) {
        Vector3 start = centre(feet, height);
        var hit = world.sweepCapsule(GameplayBulletWorld.copy(start),
                GameplayBulletWorld.copy(new Vector3(start).add(0, (float) -distance, 0)), config.radius(), height);
        return hit.filter(value -> value.normal().y() >= Math.cos(config.maxSlopeRadians())).orElse(null);
    }
    private boolean overlaps(Vector3 feet, double height) {
        return !GameplayBulletWorld.vector(world.capsulePenetration(GameplayBulletWorld.copy(centre(feet, height)),
                config.radius(), height)).isZero(0.00001f);
    }
    private static Vector3 centre(Vector3 feet, double height) { return new Vector3(feet).add(0, (float) (height / 2), 0); }
}
