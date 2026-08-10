package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.gdx.uiharness.core.action.Action;
import dev.gdx.uiharness.scene2d.Scene2dInputDispatcher;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeEvent;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ArenaRuntimeTest {
    @Test
    void damageIsAttributedToTheObservedStableContact() {
        AtomicReference<RuntimeEvent> observed = new AtomicReference<>();
        AtomicInteger enemyHealth = new AtomicInteger(-1);
        launch(new ApplicationAdapter() {
            private final ArenaApplication arena = new ArenaApplication(false, 0, null);
            private int renderedFrames;

            @Override public void create() {
                arena.create();
            }

            @Override public void render() {
                arena.render();
                renderedFrames++;
                if (renderedFrames == 1) {
                    var start = arena.stage().getRoot().findActor("start-button");
                    new Scene2dInputDispatcher(arena.stage(), arena.input())
                            .dispatch(start, new Action.Press(Input.Keys.ENTER, false));
                    arena.input().keyDown(Input.Keys.SPACE);
                    arena.input().keyUp(Input.Keys.SPACE);
                }
                var frame = arena.runtime().latestFrame().orElseThrow();
                frame.events().stream()
                        .filter(event -> event.type().value().equals(
                                "gameplay.damage-applied"))
                        .findFirst().ifPresent(event -> {
                            observed.set(event);
                            RuntimeValue value = frame.entity(EntityId.of("gameplay-enemy"))
                                    .orElseThrow().property("health-current").orElseThrow();
                            enemyHealth.set(Math.toIntExact(
                                    ((RuntimeValue.IntegerValue) value).value()));
                            Gdx.app.exit();
                        });
                if (renderedFrames > 360) {
                    Gdx.app.exit();
                }
            }

            @Override public void dispose() {
                arena.dispose();
            }
        });

        RuntimeEvent damage = observed.get();
        assertNotNull(damage, "damage event was not observed within 360 rendered frames");
        assertEquals(2, enemyHealth.get());
        assertEquals("gameplay.entity.enemy-primary",
                damage.subject().orElseThrow().value());
        assertEquals("gameplay.entity.projectile-0001",
                damage.source().orElseThrow().value());
        assertTrue(damage.attributes().stream()
                .filter(field -> field.name().equals("contact"))
                .map(RuntimeValue.Field::value)
                .map(RuntimeValue.StringValue.class::cast)
                .map(RuntimeValue.StringValue::value)
                .anyMatch("enemy-primary.collider|projectile-0001.collider"::equals));
    }

    private static void launch(ApplicationAdapter application) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("arena-runtime-qualification");
        configuration.setWindowedMode(960, 540);
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        configuration.setForegroundFPS(60);
        new Lwjgl3Application(application, configuration);
    }
}
