package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Velocity3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import java.util.Objects;

/** Fixed-tick bridge into the existing world. No entity, input or runtime authority is retained here. */
public final class GameplayBulletBridge {
    private final BulletCharacterMotor motor;
    private final IntentResolver intents;
    private final GameSystem physics = new GameSystem() {
        private final SystemDescriptor descriptor = new SystemDescriptor(SystemId.of("bullet-character-step"), SystemPhase.PHYSICS, 10);
        @Override public SystemDescriptor descriptor() { return descriptor; }
        @Override public void update(SystemContext context) {
            for (EntityView entity : context.query(Transform3D.TYPE, Velocity3D.TYPE, CharacterStance.TYPE)) {
                var pose = entity.component(Transform3D.TYPE).orElseThrow();
                var velocity = entity.component(Velocity3D.TYPE).orElseThrow();
                var stance = entity.component(CharacterStance.TYPE).orElseThrow();
                var next = motor.step(new CharacterState(pose.position(), velocity.linear(), stance.grounded(), stance.crouchFraction()),
                        intents.resolve(context, entity), context.fixedStepNanos() / 1_000_000_000.0);
                context.replace(entity.id(), Transform3D.TYPE, new Transform3D(next.feet(), pose.rotation(), pose.scale()));
                context.replace(entity.id(), Velocity3D.TYPE, new Velocity3D(next.velocity()));
                context.replace(entity.id(), CharacterStance.TYPE, new CharacterStance(next.grounded(), next.crouchFraction()));
            }
        }
    };
    public GameplayBulletBridge(GameplayBulletWorld world, CharacterConfig config, IntentResolver intents) {
        motor = new BulletCharacterMotor(world, config);
        this.intents = Objects.requireNonNull(intents, "intents");
    }
    public GameSystem physicsSystem() { return physics; }
    /** Resolves command-derived intent only; implementations must never poll input or mutate native state. */
    @FunctionalInterface public interface IntentResolver {
        CharacterIntent resolve(SystemContext context, EntityView entity);
    }
}
