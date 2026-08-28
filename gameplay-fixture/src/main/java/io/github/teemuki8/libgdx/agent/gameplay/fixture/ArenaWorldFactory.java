package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.InputMultiplexer;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dBodyFactory;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dBodySpec;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dBodyType;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dUnitConversion;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dWorldSpec;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dWorld;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentCodec;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentRegistry;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabCatalog;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabDefinition;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabParser;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.StandardComponentCodecs;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.ArenaInputSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.ArenaStateSnapshotSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.DamageSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.DeathAndScoreSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.EnemyPursuitSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.WeaponSystem;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.AnimationSystem;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.GameplayRuntimeBridge;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/** Compiles the canonical bounded arena fixture and its production input path. */
public final class ArenaWorldFactory {
    public static final int VIEWPORT_WIDTH = 960;
    public static final int VIEWPORT_HEIGHT = 540;
    public static final long FIXED_STEP_NANOS = 16_666_667L;
    public static final EntityId PLAYER_ID = EntityId.of("player");
    public static final EntityId ENEMY_ID = EntityId.of("enemy-primary");
    public static final EntityId STATE_ID = EntityId.of("arena-state");
    public static final Box2dUnitConversion UNITS = new Box2dUnitConversion(32.0);

    private static final PrefabCatalog PREFABS = readPrefabs();
    private static final ComponentRegistry COMPONENTS = components();

    private ArenaWorldFactory() {
    }

    /** Returns the once-compiled strict canonical prefab catalog. */
    public static PrefabCatalog loadPrefabs() {
        return PREFABS;
    }

    static io.github.teemuki8.libgdx.agent.gameplay.core.replay.CanonicalWorldEncoder
            canonicalEncoder() {
        return new io.github.teemuki8.libgdx.agent.gameplay.core.replay.CanonicalWorldEncoder(
                GameplayLimits.defaults().maxSnapshotBytes(), COMPONENTS);
    }

    /** Creates an application-owned arena session already in its actionable state. */
    public static ArenaSession openPlaying() {
        ArenaGameState state = new ArenaGameState();
        state.startPlaying();
        GameplayBox2dWorld nativeWorld = newNativeWorld();
        AgentRuntime runtime = AgentRuntime.builder().build();
        InputMultiplexer input = new InputMultiplexer();
        ArenaSession session = open(
                state, nativeWorld, runtime, input, null, null, true);
        runtime.start();
        return session;
    }

    /** Builds the render-thread fixture around caller-owned application resources. */
    static ArenaSession openApplication(
            ArenaGameState state,
            GameplayBox2dWorld nativeWorld,
            AgentRuntime runtime,
            InputMultiplexer input,
            GameplayRuntimeBridge gameplayRuntime,
            GameSystem visualPreparation) {
        return open(state, nativeWorld, runtime, input,
                Objects.requireNonNull(gameplayRuntime, "gameplayRuntime"),
                Objects.requireNonNull(visualPreparation, "visualPreparation"), false);
    }

    private static ArenaSession open(
            ArenaGameState state,
            GameplayBox2dWorld nativeWorld,
            AgentRuntime runtime,
            InputMultiplexer input,
            GameplayRuntimeBridge gameplayRuntime,
            GameSystem visualPreparation,
            boolean ownsNativeResources) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nativeWorld, "nativeWorld");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(input, "input");
        Box2dBodyFactory bodyFactory = new Box2dBodyFactory(UNITS, entity -> {
            Box2dBodyType type = entity.id().value().startsWith("wall-")
                    ? Box2dBodyType.STATIC : Box2dBodyType.DYNAMIC;
            return new Box2dBodySpec(type, type == Box2dBodyType.DYNAMIC ? 1.0 : 0.0,
                    0.4, 0.0, 0.0, 0.0, 1.0, false, false);
        });
        GameplayBox2dBridge physics = new GameplayBox2dBridge(
                nativeWorld, bodyFactory, UNITS, runtime, GameplayLimits.defaults());
        WeaponSystem weapons = new WeaponSystem(
                state, PREFABS.require(PrefabId.of("projectile")));

        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), COMPONENTS)
                .fixedStepNanos(FIXED_STEP_NANOS)
                .initializer(sink -> initializeWorld(sink, state))
                .lifecycleParticipant(physics)
                .system(new ArenaInputSystem(state))
                .system(new EnemyPursuitSystem(state));
        if (gameplayRuntime != null) {
            gameplayRuntime.systems().forEach(builder::system);
        }
        physics.systems().forEach(builder::system);
        builder.system(weapons)
                .system(new DamageSystem())
                .system(new DeathAndScoreSystem(state, physics))
                .system(new AnimationSystem(10))
                .system(new ArenaStateSnapshotSystem(state));
        if (visualPreparation != null) {
            builder.system(visualPreparation);
        }
        GameWorld world = builder.build();
        ArenaInputProcessor inputProcessor = new ArenaInputProcessor(world, state);
        input.addProcessor(inputProcessor);
        return new ArenaSession(
                world, nativeWorld, runtime, physics, state, inputProcessor, input, weapons,
                ownsNativeResources);
    }

    private static GameplayBox2dWorld newNativeWorld() {
        return GameplayBox2dWorld.create(new Box2dWorldSpec(Vec2.ZERO, 4, 0.1));
    }

    /** Copies one prefab while replacing explicitly supplied standard components. */
    public static EntityDraft copyWith(
            PrefabDefinition prefab,
            EntityId entityId,
            Map<ComponentType<?>, Component> overrides) {
        Objects.requireNonNull(prefab, "prefab");
        Objects.requireNonNull(overrides, "overrides");
        EntityDraft.Builder builder = EntityDraft.builder(entityId);
        prefab.components().forEach((type, component) ->
                add(builder, type, overrides.getOrDefault(type, component)));
        overrides.forEach((type, component) -> {
            if (!prefab.components().containsKey(type)) {
                add(builder, type, component);
            }
        });
        return builder.build();
    }

    private static void initializeWorld(
            io.github.teemuki8.libgdx.agent.gameplay.core.world.SpawnSink sink,
            ArenaGameState state) {
        sink.spawn(EntityDraft.builder(STATE_ID)
                .with(ArenaStateComponent.TYPE, state.snapshot())
                .build());
        sink.spawn(PREFABS.require(PrefabId.of("player")).instantiate(PLAYER_ID));
        sink.spawn(PREFABS.require(PrefabId.of("enemy")).instantiate(ENEMY_ID));
        sink.spawn(floor());
        sink.spawn(wall("wall-left", new Vec2(12, 230), new Vec2(24, 412)));
        sink.spawn(wall("wall-right", new Vec2(948, 230), new Vec2(24, 412)));
        sink.spawn(wall("wall-bottom", new Vec2(480, 12), new Vec2(960, 24)));
        sink.spawn(wall("wall-top", new Vec2(480, 448), new Vec2(960, 24)));
    }

    private static ComponentRegistry components() {
        ComponentRegistry.Builder builder = ComponentRegistry.builder();
        StandardComponents.registry().types().forEach(builder::register);
        return builder.register(ArenaStateComponent.TYPE,
                new ComponentCodec<ArenaStateComponent>() {
                    @Override public ArenaStateComponent snapshot(ArenaStateComponent value) {
                        return value;
                    }

                    @Override public void encode(
                            ArenaStateComponent value,
                            io.github.teemuki8.libgdx.agent.gameplay.core.component
                                    .CanonicalComponentWriter writer) {
                        writer.text(value.screen().name());
                        writer.decimal(value.aimDirection().x());
                        writer.decimal(value.aimDirection().y());
                        writer.longValue(value.nextFireTick());
                        writer.longValue(value.score());
                        writer.bool(value.enemyKilled());
                        writer.longValue(value.enemyDeathTick());
                        writer.text(value.enemyKillingSource());
                        writer.bool(value.playerKilled());
                    }
                }).build();
    }

    private static EntityDraft floor() {
        return EntityDraft.builder(EntityId.of("arena-floor"))
                .with(Transform2D.TYPE, new Transform2D(
                        new Vec2(480, 230), 0, new Vec2(960, 460), new Vec2(0.5, 0.5)))
                .with(Sprite.TYPE, new Sprite(
                        "art/arena.atlas", "arena-floor", new Vec2(960, 460),
                        new Vec2(0.5, 0.5)))
                .with(Render.TYPE, new Render("background", 0, Rgba.WHITE, true))
                .build();
    }

    private static EntityDraft wall(String id, Vec2 position, Vec2 size) {
        PrefabDefinition wall = PREFABS.require(PrefabId.of("wall"));
        return copyWith(wall, EntityId.of(id), Map.of(
                Transform2D.TYPE, new Transform2D(
                        position, 0, size, new Vec2(0.5, 0.5)),
                Collider.TYPE, new Collider(
                        Collider.Shape.BOX, size, Vec2.ZERO, false, 8, 7)));
    }

    private static PrefabCatalog readPrefabs() {
        PrefabLimits limits = PrefabLimits.defaults();
        try (InputStream input = ArenaWorldFactory.class.getResourceAsStream(
                "/gameplay/arena-prefabs.json")) {
            if (input == null) {
                throw new IllegalStateException("missing canonical arena prefab resource");
            }
            byte[] bytes = input.readNBytes(limits.maxDocumentBytes() + 1);
            return new PrefabParser(StandardComponentCodecs.registry(), limits).parse(bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to read canonical arena prefabs", failure);
        }
    }

    private static <T extends Component> void add(
            EntityDraft.Builder builder, ComponentType<T> type, Component component) {
        builder.with(type, type.valueClass().cast(component));
    }

    /** Owns one fixture-only world, runtime, native world, and production input multiplexer. */
    public static final class ArenaSession implements AutoCloseable {
        private GameWorld world;
        private GameplayBox2dWorld nativeWorld;
        private final AgentRuntime runtime;
        private GameplayBox2dBridge physics;
        private final ArenaGameState state;
        private ArenaInputProcessor inputProcessor;
        private final InputMultiplexer input;
        private WeaponSystem weapons;
        private final boolean ownsNativeResources;
        private boolean closed;

        private ArenaSession(
                GameWorld world,
                GameplayBox2dWorld nativeWorld,
                AgentRuntime runtime,
                GameplayBox2dBridge physics,
                ArenaGameState state,
                ArenaInputProcessor inputProcessor,
                InputMultiplexer input,
                WeaponSystem weapons,
                boolean ownsNativeResources) {
            this.world = world;
            this.nativeWorld = nativeWorld;
            this.runtime = runtime;
            this.physics = physics;
            this.state = state;
            this.inputProcessor = inputProcessor;
            this.input = input;
            this.weapons = weapons;
            this.ownsNativeResources = ownsNativeResources;
        }

        /** Returns the owner-thread gameplay world. */
        public GameWorld world() {
            return world;
        }

        /** Returns the application-owned production input multiplexer. */
        public InputMultiplexer input() {
            return input;
        }

        /** Returns independent arena state. */
        public ArenaGameState state() {
            return state;
        }

        /** Returns the active Box2D adapter for immutable/native qualification queries. */
        public GameplayBox2dBridge physics() {
            return physics;
        }

        /** Returns the caller-owned runtime used by fixture adapters. */
        public AgentRuntime runtime() {
            return runtime;
        }

        /** Requests and completes a deterministic reset into the playing state. */
        public void resetPlaying() {
            inputProcessor.reset();
            weapons.reset();
            state.reset();
            state.startPlaying();
            if (ownsNativeResources) {
                input.removeProcessor(inputProcessor);
                world.close();
                physics.close();
                nativeWorld.close();
                GameplayBox2dWorld replacementWorld = newNativeWorld();
                ArenaSession replacement = open(
                        state, replacementWorld, runtime, input, null, null, true);
                world = replacement.world;
                nativeWorld = replacement.nativeWorld;
                physics = replacement.physics;
                inputProcessor = replacement.inputProcessor;
                weapons = replacement.weapons;
                replacement.closed = true;
                return;
            }
            world.requestReset();
            world.step();
        }

        /** Disposes owned values in reverse order while adapters preserve caller ownership. */
        @Override public void close() {
            if (closed) {
                return;
            }
            input.removeProcessor(inputProcessor);
            world.close();
            physics.close();
            if (ownsNativeResources) {
                runtime.close();
                nativeWorld.close();
            }
            closed = true;
        }
    }
}
