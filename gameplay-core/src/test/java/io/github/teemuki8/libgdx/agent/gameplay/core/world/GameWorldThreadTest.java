package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GameWorldThreadTest {
    @Test
    void completedSnapshotMayCrossThreadsButMutationMayNot() {
        GameWorld world = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .build();
        world.step();

        WorldSnapshot snapshot = CompletableFuture.supplyAsync(world::snapshot).join();
        GameplayException failure = CompletableFuture.supplyAsync(() -> {
            try {
                world.spawn(EntityDraft.builder(EntityId.of("player"))
                        .with(Health.TYPE, new Health(3, 3))
                        .build());
                throw new AssertionError("off-thread spawn unexpectedly succeeded");
            } catch (GameplayException expected) {
                return expected;
            }
        }).join();

        assertNotNull(snapshot);
        assertEquals(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION, failure.code());
        world.close();
    }
}
