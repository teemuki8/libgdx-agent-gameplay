package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentRegistry;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Velocity3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayBulletBridgeTest {
    @Test void physicsSlotWritesCanonicalDomainPoseAndStance() {
        var components = ComponentRegistry.builder().register(Transform3D.TYPE).register(Velocity3D.TYPE)
                .register(CharacterStance.TYPE, CharacterStance.CODEC).build();
        try (var nativeWorld = new GameplayBulletWorld(8)) {
            nativeWorld.addBox(EntityId.of("floor"), new Transform3D(new Vec3(0, -0.5, 0),
                    new QuaternionValue(0, 0, 0, 1)), new Vec3(20, 0.5, 20));
            var bridge = new GameplayBulletBridge(nativeWorld, CharacterConfig.defaults(),
                    (context, entity) -> new CharacterIntent(new Vec3(1, 0, 0), false, false));
            try (var game = GameWorld.builder(GameplayLimits.defaults(), components)
                    .system(bridge.physicsSystem()).build()) {
                game.spawn(EntityDraft.builder(EntityId.of("player"))
                        .with(Transform3D.TYPE, new Transform3D(new Vec3(0, 0.01, 0), new QuaternionValue(0, 0, 0, 1)))
                        .with(Velocity3D.TYPE, new Velocity3D(Vec3.ZERO))
                        .with(CharacterStance.TYPE, new CharacterStance(false, false)).build());
                for (int i = 0; i < 60; i++) game.step();
                var player = game.entity(EntityId.of("player")).orElseThrow();
                assertTrue(player.component(Transform3D.TYPE).orElseThrow().position().x() > 1);
                assertTrue(player.component(CharacterStance.TYPE).orElseThrow().grounded());
            }
        }
    }
}
