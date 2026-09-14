package example;

import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.FixedStepLoop;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.GameplayRuntimeBridge;
import java.util.List;
import io.github.teemuki8.libgdx.agent.gameplay.bullet.CharacterConfig;
import io.github.teemuki8.libgdx.agent.gameplay.bullet.BulletBodyShape;
import io.github.teemuki8.libgdx.agent.gameplay.bullet.BulletDynamicsLimits;
import io.github.teemuki8.libgdx.agent.gameplay.bullet.BulletRigidBodySpec;
import io.github.teemuki8.libgdx.agent.gameplay.bullet.GameplayBulletDynamicsWorld;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletContactLimits;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.core.SimulationTimelineSpec;

/** External compile/runtime proof for all five Maven publications. */
public final class ConsumerSmoke {
    private ConsumerSmoke() {
    }

    /** Resolves every artifact and executes the native, GL-free public world contract. */
    public static void main(String[] args) {
        List<Class<?>> adapterTypes = List.of(
                FixedStepLoop.class, GameplayRuntimeBridge.class, GameplayBox2dBridge.class);
        try (GameWorld world = GameWorld.builder(
                GameplayLimits.defaults(), StandardComponents.registry()).build()) {
            if (CharacterConfig.defaults().heightAt(1) != 1.0) {
                throw new IllegalStateException("published Bullet configuration is inconsistent");
            }
            world.step();
            if (world.snapshot().tick() != 0 || adapterTypes.size() != 3) {
                throw new IllegalStateException("published gameplay contract is inconsistent");
            }
        }
        try (var runtime = AgentRuntime.builder().build();
                var dynamics = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(2, 1), new Vec3(0, -9.81, 0))) {
            runtime.simulation().register(SimulationTimelineSpec.fixedStep(16_666_667));
            dynamics.observe(runtime, "consumer", BulletContactLimits.developmentDefaults());
            dynamics.add(io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId.of("capsule"),
                    new BulletRigidBodySpec(new Transform3D(new Vec3(0, 2, 0), QuaternionValue.IDENTITY),
                            new BulletBodyShape(BulletBodyShape.Kind.CAPSULE_Y, new Vec3(.4, 1, .4)),
                            1, .7, 0, .08, .15, 1, 65535));
            runtime.start();
            runtime.simulation().tick(16_666_667, supplied -> {
                dynamics.step(supplied / 1e9);
                return supplied;
            });
            if (runtime.entity(EntityId.of("bullet.body.consumer.capsule")).isEmpty()
                    || !dynamics.contactTicks(1, 1, 1).ticks().getFirst().complete()
                    || !runtime.latestFrame().orElseThrow().stats().diagnostics().isEmpty()) {
                throw new IllegalStateException("published native Bullet inspection is inconsistent");
            }
        }
        System.out.println("verified gameplay-core/libgdx/runtime/box2d/bullet");
    }
}
