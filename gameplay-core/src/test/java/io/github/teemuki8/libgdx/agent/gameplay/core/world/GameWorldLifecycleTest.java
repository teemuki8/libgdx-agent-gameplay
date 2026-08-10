package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GameWorldLifecycleTest {
    private static final EntityId PLAYER = EntityId.of("player");

    @Test
    void spawnAndDespawnUseDocumentedBarriers() {
        List<String> lifecycle = new ArrayList<>();
        GameWorld world = baseBuilder()
                .lifecycleParticipant(observer(lifecycle))
                .system(system("lifecycle-driver", SystemPhase.GAMEPLAY, 10, context -> {
                    if (context.tick() == 0) {
                        context.spawn(playerDraft());
                    }
                    if (context.tick() == 2) {
                        context.despawn(PLAYER);
                    }
                }))
                .system(system("runtime-observer", SystemPhase.RUNTIME_CAPTURE, 10,
                        context -> lifecycle.add("runtime-visible:" + !context.query().isEmpty())))
                .build();

        assertTrue(world.entity(PLAYER).isEmpty());
        world.step();
        assertTrue(world.entity(PLAYER).isEmpty());
        world.step();
        assertEquals(EntityState.ACTIVE, world.entity(PLAYER).orElseThrow().state());
        world.step();
        assertTrue(world.entity(PLAYER).isEmpty());

        assertEquals(List.of(
                "runtime-visible:false",
                "activate:player",
                "runtime-visible:true",
                "logical-despawn:player",
                "runtime-visible:false",
                "dispose:player"), lifecycle);
        world.close();
    }

    @Test
    void resetRunsAfterTheCurrentTickAndReplaysTheInitializer() {
        List<String> lifecycle = new ArrayList<>();
        GameWorld world = baseBuilder()
                .initializer(sink -> sink.spawn(playerDraft()))
                .lifecycleParticipant(observer(lifecycle))
                .build();

        CompletedTick initial = world.step();
        assertTrue(initial.snapshot().entity(PLAYER).isPresent());
        world.requestReset();
        world.step();
        assertTrue(world.entity(PLAYER).isEmpty());

        CompletedTick firstResetTick = world.step();
        assertEquals(0, firstResetTick.snapshot().tick());
        assertTrue(firstResetTick.snapshot().entity(PLAYER).isPresent());
        assertEquals(2, lifecycle.stream().filter("activate:player"::equals).count());
        assertTrue(lifecycle.contains("reset"));
        world.close();
    }

    @Test
    void completedSnapshotsAreImmutableAndSortedByEntityId() {
        GameWorld world = baseBuilder()
                .initializer(sink -> {
                    sink.spawn(draft("zeta"));
                    sink.spawn(draft("alpha"));
                })
                .build();

        WorldSnapshot snapshot = world.step().snapshot();

        assertEquals(List.of("alpha", "zeta"), snapshot.entities().stream()
                .map(entity -> entity.id().value()).toList());
        assertFalse(snapshot.entities().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.entities().clear());
        world.close();
    }

    @Test
    void nativeDisposalRunsLeavesFirstAndReverseWithinOneDependencyLevel() {
        List<String> disposal = new ArrayList<>();
        GameWorld world = baseBuilder()
                .initializer(sink -> sink.spawn(playerDraft()))
                .lifecycleParticipant(disposalObserver("root-a", 0, disposal))
                .lifecycleParticipant(disposalObserver("leaf-a", 1, disposal))
                .lifecycleParticipant(disposalObserver("leaf-b", 1, disposal))
                .lifecycleParticipant(disposalObserver("root-b", 0, disposal))
                .build();
        world.step();
        world.despawn(PLAYER);

        world.step();

        assertEquals(List.of("leaf-b", "leaf-a", "root-b", "root-a"), disposal);
        world.close();
    }

    private static GameWorld.Builder baseBuilder() {
        return GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(16_666_667L);
    }

    private static EntityDraft playerDraft() {
        return draft("player");
    }

    private static EntityDraft draft(String id) {
        return EntityDraft.builder(EntityId.of(id))
                .with(Health.TYPE, new Health(3, 3))
                .build();
    }

    private static GameSystem system(
            String id, SystemPhase phase, int slot, java.util.function.Consumer<SystemContext> body) {
        return new GameSystem() {
            @Override
            public SystemDescriptor descriptor() {
                return new SystemDescriptor(SystemId.of(id), phase, slot);
            }

            @Override
            public void update(SystemContext context) {
                body.accept(context);
            }
        };
    }

    private static LifecycleParticipant observer(List<String> calls) {
        return new LifecycleParticipant() {
            @Override
            public void onActivate(EntityView entity) {
                calls.add("activate:" + entity.id());
            }

            @Override
            public void onLogicalDespawn(EntityView entity) {
                calls.add("logical-despawn:" + entity.id());
            }

            @Override
            public void onDispose(EntityId entityId) {
                calls.add("dispose:" + entityId);
            }

            @Override
            public void onReset() {
                calls.add("reset");
            }
        };
    }

    private static LifecycleParticipant disposalObserver(
            String name, int dependencyLevel, List<String> calls) {
        return new LifecycleParticipant() {
            @Override
            public int dependencyLevel() {
                return dependencyLevel;
            }

            @Override
            public void onDispose(EntityId entityId) {
                calls.add(name);
            }
        };
    }
}
