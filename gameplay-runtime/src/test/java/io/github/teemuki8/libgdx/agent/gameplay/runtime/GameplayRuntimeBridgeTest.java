package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.ChangeCause;
import io.github.teemuki8.libgdx.agent.runtime.core.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeStatus;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class GameplayRuntimeBridgeTest {
    @Test
    void rejectsBridgeAccessFromAThreadOtherThanItsOwner() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            CompletionException wrapper = assertThrows(CompletionException.class,
                    () -> CompletableFuture.runAsync(bridge::systems).join());
            GameplayException failure = (GameplayException) wrapper.getCause();
            assertEquals(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION, failure.code());
        }
        runtime.close();
    }

    @Test
    void projectsDomainAndUnavailableVisualValuesUnderOneFrameToken() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            runtime.start();
            try (GameWorld world = world(bridge)) {
                world.step();
            }

            var frame = runtime.latestFrame().orElseThrow();
            EntitySnapshot player = frame.entity(runtimeId("gameplay.entity.player"))
                    .orElseThrow();
            EntitySnapshot visual = frame.entity(runtimeId("gameplay.visual.player"))
                    .orElseThrow();
            assertEquals(3L, ((RuntimeValue.IntegerValue) player
                    .property("health.current").orElseThrow()).value());
            assertEquals("gameplay-frame-0", ((RuntimeValue.StringValue) player
                    .property("frameToken").orElseThrow()).value());
            assertEquals("UNAVAILABLE", ((RuntimeValue.EnumValue) visual
                    .property("status").orElseThrow()).value());
            assertEquals("gameplay-frame-0", ((RuntimeValue.StringValue) visual
                    .property("frameToken").orElseThrow()).value());
        }
        assertEquals(RuntimeStatus.RUNNING, runtime.status());
        runtime.frame(1, () -> { });
        assertFalse(runtime.latestFrame().orElseThrow().entities().stream()
                .anyMatch(entity -> entity.id().value().startsWith("gameplay.")));
        runtime.close();
    }

    @Test
    void duplicateInstallationFailsAndCloseRemovesOnlyBridgeSources() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults());

        GameplayException failure = assertThrows(GameplayException.class,
                () -> new GameplayRuntimeBridge(
                        runtime, StandardRuntimeProjections.registry(),
                        GameplayLimits.defaults()));
        assertEquals(GameplayDiagnosticCode.RUNTIME_DUPLICATE_INSTALLATION, failure.code());

        bridge.close();
        runtime.start();
        assertFalse(runtime.latestFrame().orElseThrow().entities().stream()
                .anyMatch(entity -> entity.id().value().startsWith("gameplay.")));
        runtime.close();
    }

    @Test
    void missingRenderPrepEvidenceFailsTypedAndCanBeCleanedUp() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            runtime.start();
            GameWorld.Builder builder = GameWorld.builder(
                            GameplayLimits.defaults(), StandardComponents.registry())
                    .initializer(sink -> sink.spawn(EntityDraft.builder(EntityId.of("player"))
                            .with(Health.TYPE, new Health(3, 3))
                            .build()));
            bridge.systems().forEach(builder::system);
            try (GameWorld world = builder.build()) {
                GameplayException failure = assertThrows(
                        GameplayException.class, world::step);
                assertEquals(GameplayDiagnosticCode.RUNTIME_FRAME_INCOMPLETE,
                        failure.code());
            }
        }
        assertEquals(RuntimeStatus.RUNNING, runtime.status());
        runtime.close();
    }

    @Test
    void damageEventExplicitlyCausesTheProjectedHealthChange() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            GameWorld.Builder builder = GameWorld.builder(
                            GameplayLimits.defaults(), StandardComponents.registry())
                    .initializer(sink -> sink.spawn(EntityDraft.builder(EntityId.of("player"))
                            .with(Health.TYPE, new Health(3, 3))
                            .build()))
                    .system(damageSystem());
            bridge.systems().forEach(builder::system);
            builder.system(visualPreparation(bridge));
            runtime.start();
            try (GameWorld world = builder.build()) {
                world.step();
                world.step();
            }

            var healthChange = runtime.latestFrame().orElseThrow().changes().stream()
                    .filter(change -> change.property().filter(
                            "health.current"::equals).isPresent())
                    .findFirst().orElseThrow();
            assertEquals(ChangeCause.Kind.EVENT, healthChange.cause().kind());
        }
        runtime.close();
    }

    @Test
    void collisionEventRetainsBothRuntimeEndpointsAndFixtureIds() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            GameWorld.Builder builder = GameWorld.builder(
                            GameplayLimits.defaults(), StandardComponents.registry())
                    .initializer(sink -> {
                        sink.spawn(EntityDraft.builder(EntityId.of("alpha"))
                                .with(Health.TYPE, new Health(1, 1)).build());
                        sink.spawn(EntityDraft.builder(EntityId.of("beta"))
                                .with(Health.TYPE, new Health(1, 1)).build());
                    })
                    .system(new GameSystem() {
                        @Override public SystemDescriptor descriptor() {
                            return new SystemDescriptor(SystemId.of("emit-collision"),
                                    SystemPhase.POST_PHYSICS, 20);
                        }

                        @Override public void update(SystemContext context) {
                            context.emit(new CollisionStarted(
                                    EntityId.of("alpha"), EntityId.of("beta"),
                                    "alpha.collider", "beta.collider"));
                        }
                    });
            bridge.systems().forEach(builder::system);
            builder.system(visualPreparation(bridge));
            runtime.start();
            try (GameWorld world = builder.build()) {
                world.step();
            }

            var event = runtime.latestFrame().orElseThrow().events().stream()
                    .filter(candidate -> candidate.type().value()
                            .equals("gameplay.collision-started"))
                    .findFirst().orElseThrow();
            assertEquals("gameplay.entity.alpha", event.subject().orElseThrow().value());
            assertEquals("gameplay.entity.beta", event.source().orElseThrow().value());
            assertEquals(List.of("firstFixtureId", "secondFixtureId"),
                    event.attributes().stream().map(RuntimeValue.Field::name).toList());
        }
        runtime.close();
    }

    static GameWorld world(GameplayRuntimeBridge bridge) {
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(EntityDraft.builder(EntityId.of("player"))
                        .with(Transform2D.TYPE, new Transform2D(
                                new Vec2(2, 3), 0, new Vec2(1, 1),
                                new Vec2(0.5, 0.5)))
                        .with(Health.TYPE, new Health(3, 3))
                        .build()));
        bridge.systems().forEach(builder::system);
        builder.system(visualPreparation(bridge));
        return builder.build();
    }

    private static GameSystem visualPreparation(GameplayRuntimeBridge bridge) {
        return new GameSystem() {
            @Override
            public SystemDescriptor descriptor() {
                return new SystemDescriptor(
                        SystemId.of("visual-evidence"), SystemPhase.RENDER_PREP, 10);
            }

            @Override
            public void update(SystemContext context) {
                bridge.prepareVisuals(new WorldVisualSnapshot(context.tick(), List.of()));
            }
        };
    }

    private static GameSystem damageSystem() {
        return new GameSystem() {
            @Override
            public SystemDescriptor descriptor() {
                return new SystemDescriptor(
                        SystemId.of("damage-player"), SystemPhase.GAMEPLAY, 10);
            }

            @Override
            public void update(SystemContext context) {
                var player = context.query(Health.TYPE).getFirst();
                Health health = player.component(Health.TYPE).orElseThrow();
                context.replace(player.id(), Health.TYPE,
                        new Health(health.current() - 1, health.max()));
                context.emit(new DamageApplied(player.id(), player.id(), 1));
            }
        };
    }

    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId runtimeId(
            String value) {
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }
}
