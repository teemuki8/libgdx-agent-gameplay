package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import dev.gdx.markup.core.BuiltUi;
import dev.gdx.markup.core.DefaultSkin;
import dev.gdx.markup.core.MarkupBuilder;
import dev.gdx.markup.core.MarkupDocument;
import dev.gdx.markup.core.MarkupParser;
import dev.gdx.markup.core.style.CssParser;
import dev.gdx.markup.harness.HarnessSemanticSink;
import dev.gdx.markup.runtime.MarkupRuntimeSource;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.AssetResolver;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.FixedStepLoop;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.GameplayRenderer;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.VisualSnapshotBuilder;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.GameplayRuntimeBridge;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.SessionId;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Running markup-only qualification game with deterministic gameplay and evidence capture. */
public final class ArenaApplication extends ApplicationAdapter {
    private static final int BACKGROUND_RGBA8888 = 0x09131fff;

    private final boolean mcp;
    private final int smokeFrames;
    private final String screenshotPath;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong frame = new AtomicLong();

    private Stage stage;
    private InputMultiplexer input;
    private OrthographicCamera camera;
    private TextureAtlas atlas;
    private AssetResolver assets;
    private GameplayRenderer renderer;
    private Skin skin;
    private Group uiRoot;
    private AgentRuntime runtime;
    private World nativeWorld;
    private ArenaGameState state;
    private GameplayRuntimeBridge gameplayRuntime;
    private ArenaRuntimeProjection runtimeProjection;
    private ArenaWorldFactory.ArenaSession arena;
    private ArenaHarness harness;
    private MarkupRuntimeSource markupRuntime;
    private FixedStepLoop fixedStep;
    private Label screenValue;
    private Label healthValue;
    private Label enemyHealthValue;
    private Label scoreValue;
    private TextButton startButton;
    private TextButton resetButton;
    private Actor titleOverlay;
    private Actor gameOverOverlay;
    private boolean forceTick;
    private int framesLeft;

    /** Creates one launch mode; resources are allocated later on the render thread. */
    public ArenaApplication(boolean mcp, int smokeFrames, String screenshotPath) {
        if (smokeFrames < 0) {
            throw new IllegalArgumentException("smokeFrames must not be negative");
        }
        this.mcp = mcp;
        this.smokeFrames = smokeFrames;
        this.screenshotPath = screenshotPath;
    }

    @Override public void create() {
        camera = new OrthographicCamera();
        FitViewport viewport = new FitViewport(
                ArenaWorldFactory.VIEWPORT_WIDTH,
                ArenaWorldFactory.VIEWPORT_HEIGHT,
                camera);
        SpriteBatch batch = new SpriteBatch();
        stage = new Stage(viewport, batch);
        input = new InputMultiplexer(stage);
        atlas = new TextureAtlas(Gdx.files.internal("art/arena.atlas"));
        assets = new AssetResolver(atlas);
        assets.useNearestFiltering();
        renderer = new GameplayRenderer(batch, camera, assets);
        state = new ArenaGameState();
        runtime = AgentRuntime.builder()
                .sessionId(SessionId.of(ArenaHarness.SESSION_ID))
                .build();
        gameplayRuntime = new GameplayRuntimeBridge(
                runtime, ArenaRuntimeProjections.registry(), GameplayLimits.defaults());
        runtimeProjection = new ArenaRuntimeProjection(
                runtime, gameplayRuntime,
                new VisualSnapshotBuilder(
                        camera, assets,
                        ArenaWorldFactory.VIEWPORT_WIDTH,
                        ArenaWorldFactory.VIEWPORT_HEIGHT,
                        ArenaWorldFactory.UNITS.renderUnitsPerMeter()));
        nativeWorld = new World(new Vector2(), true);
        arena = ArenaWorldFactory.openApplication(
                state, nativeWorld, runtime, input, gameplayRuntime, runtimeProjection);
        runtime.start();
        harness = new ArenaHarness(stage, input, revision::get, frame::get, runtime);
        Gdx.input.setInputProcessor(input);

        MarkupDocument document = new MarkupParser().parse(
                Gdx.files.internal("ui/hud.xml").readString("UTF-8"));
        skin = DefaultSkin.create();
        BuiltUi built = MarkupBuilder.build(
                document,
                new CssParser().parse(
                        Gdx.files.internal("ui/hud.gdxcss").readString("UTF-8")),
                skin,
                new HarnessSemanticSink(
                        harness.session().semantics(), ArenaHarness.CORRELATION_TOKEN));
        uiRoot = built.root();
        sizeLayoutRoots();
        stage.addActor(uiRoot);
        configureTouchability(stage.getRoot());
        markupRuntime = MarkupRuntimeSource.registerBindings(
                runtime, document, built, ArenaHarness.SESSION_ID);
        resolveMarkupActors();
        wireActions();
        gameOverOverlay.setVisible(false);
        stage.setKeyboardFocus(startButton);

        fixedStep = new FixedStepLoop(
                Duration.ofNanos(ArenaWorldFactory.FIXED_STEP_NANOS), 5);
        stepWorld();
        updateHud();
        framesLeft = smokeFrames;
        if (mcp) {
            harness.startMcp();
        }
    }

    @Override public void render() {
        harness.drain();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }

        Duration elapsed = Duration.ofNanos(Math.max(0L,
                Math.round(Math.min(Gdx.graphics.getDeltaTime(), 0.25f) * 1_000_000_000.0)));
        int steps = fixedStep.advance(elapsed, this::stepWorld);
        boolean capturedRuntime = steps > 0;
        if (forceTick && steps == 0) {
            stepWorld();
            capturedRuntime = true;
        }
        forceTick = false;
        updateHud();
        if (!capturedRuntime) {
            captureRenderedState();
        }

        float actorDelta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        stage.act(actorDelta);
        stage.getViewport().apply(true);
        int color = BACKGROUND_RGBA8888;
        Gdx.gl.glClearColor(
                ((color >>> 24) & 0xff) / 255f,
                ((color >>> 16) & 0xff) / 255f,
                ((color >>> 8) & 0xff) / 255f,
                (color & 0xff) / 255f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(arena.world().snapshot());
        stage.draw();

        frame.incrementAndGet();
        harness.publishFrame();
        if (framesLeft > 0 && --framesLeft == 0) {
            writeScreenshot();
            Gdx.app.exit();
        }
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        sizeLayoutRoots();
    }

    @Override public void dispose() {
        close(harness);
        close(markupRuntime);
        close(arena);
        close(runtimeProjection);
        close(gameplayRuntime);
        close(runtime);
        close(renderer);
        if (atlas != null) {
            atlas.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (nativeWorld != null) {
            nativeWorld.dispose();
        }
    }

    Stage stage() {
        return stage;
    }

    InputMultiplexer input() {
        return input;
    }

    ArenaGameState state() {
        return state;
    }

    AgentRuntime runtime() {
        return runtime;
    }

    ArenaHarness harness() {
        return harness;
    }

    long revision() {
        return revision.get();
    }

    long frame() {
        return frame.get();
    }

    private void resolveMarkupActors() {
        screenValue = requireActor("screen-value", Label.class);
        healthValue = requireActor("health-value", Label.class);
        enemyHealthValue = requireActor("enemy-health-value", Label.class);
        scoreValue = requireActor("score-value", Label.class);
        startButton = requireActor("start-button", TextButton.class);
        resetButton = requireActor("reset-button", TextButton.class);
        titleOverlay = requireActor("title-overlay", Actor.class);
        gameOverOverlay = requireActor("game-over-overlay", Actor.class);
    }

    private void wireActions() {
        startButton.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ENTER) {
                    return false;
                }
                startGame();
                return true;
            }
        });
        startButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                startGame();
            }
        });
        resetButton.addListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ENTER) {
                    return false;
                }
                resetArena();
                return true;
            }
        });
        resetButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                resetArena();
            }
        });
    }

    private void startGame() {
        if (state.screen() == ArenaGameState.Screen.TITLE) {
            state.startPlaying();
            forceTick = true;
            titleOverlay.setVisible(false);
            stage.setKeyboardFocus(resetButton);
        }
    }

    private void resetArena() {
        arena.close();
        nativeWorld.dispose();
        state.reset();
        state.startPlaying();
        nativeWorld = new World(new Vector2(), true);
        arena = ArenaWorldFactory.openApplication(
                state, nativeWorld, runtime, input, gameplayRuntime, runtimeProjection);
        stepWorld();
        forceTick = false;
        titleOverlay.setVisible(false);
        gameOverOverlay.setVisible(false);
        stage.setKeyboardFocus(resetButton);
    }

    private void updateHud() {
        var snapshot = arena.world().snapshot();
        ArenaStateComponent arenaState = snapshot.entity(ArenaWorldFactory.STATE_ID)
                .flatMap(entity -> entity.component(ArenaStateComponent.TYPE))
                .orElseGet(state::snapshot);
        String screen = arenaState.screen().name();
        String health = Long.toString(snapshot
                .entity(ArenaWorldFactory.PLAYER_ID)
                .flatMap(entity -> entity.component(Health.TYPE))
                .map(Health::current)
                .orElse(0L));
        String enemyHealth = Long.toString(snapshot
                .entity(ArenaWorldFactory.ENEMY_ID)
                .flatMap(entity -> entity.component(Health.TYPE))
                .map(Health::current)
                .orElse(0L));
        boolean changed = setText(screenValue, screen);
        changed |= setText(healthValue, health);
        changed |= setText(enemyHealthValue, enemyHealth);
        changed |= setText(scoreValue, Long.toString(arenaState.score()));
        boolean gameOver = arenaState.screen() == ArenaGameState.Screen.GAME_OVER;
        if (gameOverOverlay.isVisible() != gameOver) {
            gameOverOverlay.setVisible(gameOver);
            changed = true;
        }
        if (changed) {
            revision.incrementAndGet();
        }
    }

    private void stepWorld() {
        arena.world().step();
        revision.incrementAndGet();
    }

    private void captureRenderedState() {
        runtime.beginFrame(ArenaWorldFactory.FIXED_STEP_NANOS);
        runtime.endFrame();
    }

    private static boolean setText(Label label, String value) {
        if (label.getText().toString().equals(value)) {
            return false;
        }
        label.setText(value);
        return true;
    }

    private <T extends Actor> T requireActor(String id, Class<T> type) {
        Actor actor = uiRoot.findActor(id);
        if (!type.isInstance(actor)) {
            throw new IllegalStateException(
                    "markup actor " + id + " is not a " + type.getSimpleName());
        }
        return type.cast(actor);
    }

    private static void configureTouchability(Actor actor) {
        if (actor instanceof Button) {
            actor.setTouchable(Touchable.enabled);
            if (actor instanceof Group group) {
                for (Actor child : group.getChildren()) {
                    disableSubtree(child);
                }
            }
            return;
        }
        if (actor instanceof Group group) {
            actor.setTouchable(Touchable.childrenOnly);
            for (Actor child : group.getChildren()) {
                configureTouchability(child);
            }
        } else {
            actor.setTouchable(Touchable.disabled);
        }
    }

    private static void disableSubtree(Actor actor) {
        actor.setTouchable(Touchable.disabled);
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                disableSubtree(child);
            }
        }
    }

    private void sizeLayoutRoots() {
        float width = stage.getViewport().getWorldWidth();
        float height = stage.getViewport().getWorldHeight();
        stage.getRoot().setSize(width, height);
        if (uiRoot != null) {
            uiRoot.setSize(width, height);
        }
    }

    private void writeScreenshot() {
        Objects.requireNonNull(screenshotPath, "screenshotPath");
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap raw = Pixmap.createFromFrameBuffer(0, 0, width, height);
        Pixmap flipped = new Pixmap(width, height, raw.getFormat());
        for (int y = 0; y < height; y++) {
            flipped.drawPixmap(raw, 0, height - 1 - y, 0, y, width, 1);
        }
        raw.dispose();
        PixmapIO.writePNG(Gdx.files.local(screenshotPath), flipped);
        flipped.dispose();
    }

    private static void close(AutoCloseable value) {
        if (value == null) {
            return;
        }
        try {
            value.close();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("failed to close arena resource", failure);
        }
    }
}
