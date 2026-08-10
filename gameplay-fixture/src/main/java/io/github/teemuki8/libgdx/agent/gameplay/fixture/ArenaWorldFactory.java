package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dBodyFactory;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dSolverSettings;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.Box2dUnitConversion;
import io.github.teemuki8.libgdx.agent.gameplay.box2d.GameplayBox2dBridge;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
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
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.ArenaInputSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.DamageSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.DeathAndScoreSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.EnemyPursuitSystem;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.system.WeaponSystem;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.AnimationSystem;
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
    public static final Box2dUnitConversion UNITS = new Box2dUnitConversion(32.0);

    private static final PrefabCatalog PREFABS = readPrefabs();

    private ArenaWorldFactory() {
    }

    /** Returns the once-compiled strict canonical prefab catalog. */
    public static PrefabCatalog loadPrefabs() {
        return PREFABS;
    }

    /** Creates an application-owned arena session already in its actionable state. */
    public static ArenaSession openPlaying() {
        ArenaGameState state = new ArenaGameState();
        state.startPlaying();
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        Box2dBodyFactory bodyFactory = new Box2dBodyFactory(UNITS, entity ->
                entity.id().value().startsWith("wall-")
                        ? BodyDef.BodyType.StaticBody : BodyDef.BodyType.DynamicBody);
        GameplayBox2dBridge physics = new GameplayBox2dBridge(
                nativeWorld, bodyFactory, UNITS, new Box2dSolverSettings(6, 2),
                runtime, GameplayLimits.defaults());
        WeaponSystem weapons = new WeaponSystem(
                state, PREFABS.require(PrefabId.of("projectile")));

        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(FIXED_STEP_NANOS)
                .initializer(ArenaWorldFactory::initializeWorld)
                .lifecycleParticipant(physics)
                .system(new ArenaInputSystem(state))
                .system(new EnemyPursuitSystem(state, physics));
        physics.systems().forEach(builder::system);
        builder.system(weapons)
                .system(new DamageSystem())
                .system(new DeathAndScoreSystem(state, physics))
                .system(new AnimationSystem(10));
        GameWorld world = builder.build();
        ArenaInputProcessor inputProcessor = new ArenaInputProcessor(world, state);
        InputMultiplexer input = new InputMultiplexer(inputProcessor);
        nativeWorld.setContactListener(physics.contactListener());
        runtime.start();
        return new ArenaSession(
                world, nativeWorld, runtime, physics, state, inputProcessor, input, weapons);
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
            io.github.teemuki8.libgdx.agent.gameplay.core.world.SpawnSink sink) {
        sink.spawn(PREFABS.require(PrefabId.of("player")).instantiate(PLAYER_ID));
        sink.spawn(PREFABS.require(PrefabId.of("enemy")).instantiate(ENEMY_ID));
        sink.spawn(floor());
        sink.spawn(wall("wall-left", new Vec2(12, 230), new Vec2(24, 412)));
        sink.spawn(wall("wall-right", new Vec2(948, 230), new Vec2(24, 412)));
        sink.spawn(wall("wall-bottom", new Vec2(480, 12), new Vec2(960, 24)));
        sink.spawn(wall("wall-top", new Vec2(480, 448), new Vec2(960, 24)));
    }

    private static EntityDraft floor() {
        return EntityDraft.builder(EntityId.of("arena-floor"))
                .with(Transform2D.TYPE, new Transform2D(
                        new Vec2(480, 230), 0, new Vec2(960, 460), new Vec2(0.5, 0.5)))
                .with(Sprite.TYPE, new Sprite(
                        "art/arena.atlas", "arena-floor", new Vec2(960, 460),
                        new Vec2(0.5, 0.5)))
                .with(Render.TYPE, new Render("floor", 0, Rgba.WHITE, true))
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
        private final GameWorld world;
        private final World nativeWorld;
        private final AgentRuntime runtime;
        private final GameplayBox2dBridge physics;
        private final ArenaGameState state;
        private final ArenaInputProcessor inputProcessor;
        private final InputMultiplexer input;
        private final WeaponSystem weapons;
        private boolean closed;

        private ArenaSession(
                GameWorld world,
                World nativeWorld,
                AgentRuntime runtime,
                GameplayBox2dBridge physics,
                ArenaGameState state,
                ArenaInputProcessor inputProcessor,
                InputMultiplexer input,
                WeaponSystem weapons) {
            this.world = world;
            this.nativeWorld = nativeWorld;
            this.runtime = runtime;
            this.physics = physics;
            this.state = state;
            this.inputProcessor = inputProcessor;
            this.input = input;
            this.weapons = weapons;
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

        /** Requests and completes a deterministic reset into the playing state. */
        public void resetPlaying() {
            inputProcessor.reset();
            weapons.reset();
            state.reset();
            state.startPlaying();
            world.requestReset();
            world.step();
            world.step();
        }

        /** Disposes owned values in reverse order while adapters preserve caller ownership. */
        @Override public void close() {
            if (closed) {
                return;
            }
            world.close();
            physics.close();
            runtime.close();
            nativeWorld.dispose();
            closed = true;
        }
    }
}
