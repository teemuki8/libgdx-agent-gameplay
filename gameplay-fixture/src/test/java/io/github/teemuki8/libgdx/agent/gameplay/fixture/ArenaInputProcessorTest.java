package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ArenaInputProcessorTest {
    @BeforeAll
    static void initializeNatives() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @Test
    void aRealWKeyPairRetainsExactlyOneTickOfUpwardIntent() {
        try (ArenaWorldFactory.ArenaSession arena = ArenaWorldFactory.openPlaying()) {
            arena.world().step();
            double startY = arena.world().snapshot().entity(ArenaWorldFactory.PLAYER_ID)
                    .orElseThrow().component(Transform2D.TYPE).orElseThrow().position().y();

            arena.input().keyDown(Input.Keys.W);
            arena.input().keyUp(Input.Keys.W);
            var moved = arena.world().step().snapshot()
                    .entity(ArenaWorldFactory.PLAYER_ID).orElseThrow();
            assertTrue(moved.component(Transform2D.TYPE).orElseThrow().position().y() > startY);

            var stopped = arena.world().step().snapshot()
                    .entity(ArenaWorldFactory.PLAYER_ID).orElseThrow();
            assertEquals(0.0, stopped.component(Movement.TYPE)
                    .orElseThrow().velocity().y(), 0.0001);
        }
    }
}
