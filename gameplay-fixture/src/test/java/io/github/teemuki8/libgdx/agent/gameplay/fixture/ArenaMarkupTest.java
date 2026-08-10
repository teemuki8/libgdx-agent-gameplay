package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.gdx.uiharness.core.locator.Locator;
import dev.gdx.uiharness.core.locator.StrictResolution;
import dev.gdx.uiharness.core.locator.TextMatch;
import dev.gdx.uiharness.core.model.Role;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ArenaMarkupTest {
    @Test
    void markupBuildsTheEntireHudWithStableSemantics() {
        AtomicInteger verified = new AtomicInteger();
        launch(new ApplicationAdapter() {
            private final ArenaApplication arena = new ArenaApplication(false, 0, null);

            @Override public void create() {
                arena.create();
            }

            @Override public void render() {
                arena.render();
                var snapshot = arena.harness().session().snapshot(
                        arena.revision(), arena.frame());
                var locators = new StrictResolution();
                assertEquals("Start game", locators.resolveStrict(snapshot,
                        Locator.role(Role.BUTTON).withName(TextMatch.exact("Start game")))
                        .accessibleName());
                assertEquals("score-value", locators.resolveStrict(
                        snapshot, Locator.testId("score-value")).testId());
                assertEquals("health-value", locators.resolveStrict(
                        snapshot, Locator.testId("health-value")).testId());
                assertEquals("Reset arena", locators.resolveStrict(snapshot,
                        Locator.role(Role.BUTTON).withName(TextMatch.exact("Reset arena")))
                        .accessibleName());
                verified.incrementAndGet();
                Gdx.app.exit();
            }

            @Override public void dispose() {
                arena.dispose();
            }
        });
        assertEquals(1, verified.get());
    }

    private static void launch(ApplicationAdapter application) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("arena-markup-test");
        configuration.setWindowedMode(960, 540);
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        new Lwjgl3Application(application, configuration);
    }
}
