package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ArenaBox2dTest {
    @BeforeAll
    static void initializeNatives() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @Test
    void inspectedPlayerFixtureMatchesDeclaredVisualAndUnitScale() {
        try (ArenaWorldFactory.ArenaSession arena = ArenaWorldFactory.openPlaying()) {
            arena.world().step();
            var player = arena.world().snapshot().entity(ArenaWorldFactory.PLAYER_ID)
                    .orElseThrow();
            Collider collider = player.component(Collider.TYPE).orElseThrow();
            Sprite sprite = player.component(Sprite.TYPE).orElseThrow();
            var body = arena.physics().bodyState(ArenaWorldFactory.PLAYER_ID).orElseThrow();

            assertEquals("player.collider", body.fixtureId());
            assertEquals(Collider.Shape.BOX, body.colliderShape());
            assertEquals(collider.size(), body.colliderSize());
            assertEquals(collider.offset(), body.colliderOffset());
            assertTrue(collider.size().x() <= sprite.visualSize().x());
            assertTrue(collider.size().y() <= sprite.visualSize().y());

            arena.runtime().beginFrame(ArenaWorldFactory.FIXED_STEP_NANOS);
            arena.runtime().endFrame();
            Set<String> runtimeIds = arena.runtime().latestFrame().orElseThrow().entities()
                    .stream().map(entity -> entity.id().value()).collect(Collectors.toSet());
            assertTrue(runtimeIds.containsAll(Set.of(
                    "box2d.body.player", "box2d.fixture.player.collider")));
            RuntimeValue bodyId = arena.runtime().latestFrame().orElseThrow()
                    .entity(EntityId.of("box2d.body.player")).orElseThrow()
                    .property("id").orElseThrow();
            assertEquals("player", ((RuntimeValue.StringValue) bodyId).value());
        }
    }
}
