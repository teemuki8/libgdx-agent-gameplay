package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnimationSystemTest {
    @Test
    void advancesFramesOnlyFromFixedSimulationTicks() {
        EntityId player = EntityId.of("player");
        Animation animation = new Animation(Map.of(
                "idle", new AnimationClip(List.of("idle-0", "idle-1"), 3, true)),
                "idle", 0, 0);
        try (GameWorld world = GameWorld.builder(
                GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(EntityDraft.builder(player)
                        .with(Animation.TYPE, animation)
                        .build()))
                .system(new AnimationSystem(10))
                .build()) {
            world.step();
            world.step();
            var third = world.step();

            Animation captured = third.snapshot().entity(player).orElseThrow()
                    .component(Animation.TYPE).orElseThrow();
            assertEquals(3, captured.elapsedTicks());
            assertEquals(1, captured.frameIndex());
        }
    }
}
