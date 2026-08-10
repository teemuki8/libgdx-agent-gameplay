package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.gdx.uiharness.agentruntime.AgentRuntimeObservationSource;
import dev.gdx.uiharness.core.action.Action;
import dev.gdx.uiharness.core.locator.Locator;
import dev.gdx.uiharness.core.locator.StrictResolution;
import dev.gdx.uiharness.core.runtime.DisplayedRuntimeComparison;
import dev.gdx.uiharness.core.runtime.RuntimeComparator;
import dev.gdx.uiharness.scene2d.Scene2dInputDispatcher;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ArenaApplicationTest {
    @Test
    void startUsesRealScene2dInputAndRuntimeComparisonDetectsDivergence() {
        AtomicReference<ArenaGameState.Screen> screen = new AtomicReference<>();
        AtomicReference<DisplayedRuntimeComparison.Status> equal = new AtomicReference<>();
        AtomicReference<DisplayedRuntimeComparison.Status> mismatch = new AtomicReference<>();
        launch(new ApplicationAdapter() {
            private final ArenaApplication arena = new ArenaApplication(false, 0, null);

            @Override public void create() {
                arena.create();
            }

            @Override public void render() {
                arena.render();
                var start = arena.stage().getRoot().findActor("start-button");
                new Scene2dInputDispatcher(arena.stage(), arena.input())
                        .dispatch(start, new Action.Press(Input.Keys.ENTER, false));
                arena.render();
                screen.set(arena.state().screen());
                RuntimeComparator comparator = new RuntimeComparator(
                        new AgentRuntimeObservationSource(
                                arena.runtime(), ArenaHarness.SESSION_ID));
                equal.set(comparator.compare(arena.harness().session().snapshot(
                                arena.revision(), arena.frame()),
                        Locator.testId("score-value"), new StrictResolution()).status());
                arena.setDisplayedScoreOverrideForTest(999L);
                arena.render();
                mismatch.set(comparator.compare(arena.harness().session().snapshot(
                                arena.revision(), arena.frame()),
                        Locator.testId("score-value"), new StrictResolution()).status());
                Gdx.app.exit();
            }

            @Override public void dispose() {
                arena.dispose();
            }
        });

        assertEquals(ArenaGameState.Screen.PLAYING, screen.get());
        assertFalse(screen.get() == ArenaGameState.Screen.TITLE);
        assertEquals(DisplayedRuntimeComparison.Status.EQUAL, equal.get());
        assertEquals(DisplayedRuntimeComparison.Status.MISMATCH, mismatch.get());
    }

    private static void launch(ApplicationAdapter application) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("arena-application-test");
        configuration.setWindowedMode(960, 540);
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        new Lwjgl3Application(application, configuration);
    }
}
