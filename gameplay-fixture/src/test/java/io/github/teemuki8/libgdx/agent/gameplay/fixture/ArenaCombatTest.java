package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.box2d.Box2d;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityKilled;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ArenaCombatTest {
    @BeforeAll
    static void initializeNatives() {
        Box2d.initialize();
    }

    @Test
    void threeProductionFirePressesKillTheEnemyAndAwardScore() {
        try (ArenaWorldFactory.ArenaSession arena = ArenaWorldFactory.openPlaying()) {
            List<EventEnvelope> evidence = new ArrayList<>(arena.world().step().events());
            for (int expectedHits = 1; expectedHits <= 3; expectedHits++) {
                int targetHits = expectedHits;
                arena.input().keyDown(Input.Keys.SPACE);
                arena.input().keyUp(Input.Keys.SPACE);
                advanceUntil(arena, evidence,
                        () -> count(evidence, DamageApplied.class) >= targetHits, 240);
            }
            advanceUntil(arena, evidence,
                    () -> arena.world().entity(ArenaWorldFactory.ENEMY_ID).isEmpty(), 120);

            assertEquals(3, count(evidence, DamageApplied.class));
            assertEquals(1, count(evidence, EntityKilled.class));
            assertEquals(300, arena.state().score());
            assertTrue(arena.world().entity(ArenaWorldFactory.ENEMY_ID).isEmpty());
        }
    }

    private static void advanceUntil(
            ArenaWorldFactory.ArenaSession arena,
            List<EventEnvelope> evidence,
            java.util.function.BooleanSupplier complete,
            int maxTicks) {
        for (int index = 0; index < maxTicks && !complete.getAsBoolean(); index++) {
            evidence.addAll(arena.world().step().events());
        }
        assertTrue(complete.getAsBoolean(), "arena condition did not complete within " + maxTicks);
    }

    private static long count(List<EventEnvelope> events, Class<?> type) {
        return events.stream().filter(event -> type.isInstance(event.event())).count();
    }
}
