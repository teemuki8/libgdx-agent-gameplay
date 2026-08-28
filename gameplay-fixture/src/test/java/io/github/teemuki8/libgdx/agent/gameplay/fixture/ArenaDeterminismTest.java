package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.box2d.Box2d;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.teemuki8.libgdx.agent.gameplay.core.replay.CanonicalWorldEncoder;
import io.github.teemuki8.libgdx.agent.gameplay.core.replay.TranscriptResult;
import io.github.teemuki8.libgdx.agent.gameplay.core.replay.WorldDigest;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ArenaDeterminismTest {
    @BeforeAll
    static void initializeNatives() {
        Box2d.initialize();
    }

    @Test
    void resetAndReplayMatchEveryTickAndEventDigest() throws IOException {
        JsonNode transcript = new ObjectMapper().readTree(
                ArenaDeterminismTest.class.getResourceAsStream(
                        "/transcripts/arena-three-hit.json"));
        try (ArenaWorldFactory.ArenaSession arena = ArenaWorldFactory.openPlaying()) {
            RunEvidence first = run(arena, transcript);
            assertEquals(300, arena.state().score());
            assertTrue(arena.world().entity(ArenaWorldFactory.ENEMY_ID).isEmpty());

            arena.resetPlaying();
            RunEvidence second = run(arena, transcript);
            assertDigestSeries(first, second, true);
            assertDigestSeries(first, second, false);
            assertEquals(300, arena.state().score());
        }
    }

    private static void assertDigestSeries(
            RunEvidence expectedRun, RunEvidence actualRun, boolean world) {
        List<WorldDigest> expected = world
                ? expectedRun.result().tickDigests() : expectedRun.result().eventDigests();
        List<WorldDigest> actual = world
                ? actualRun.result().tickDigests() : actualRun.result().eventDigests();
        String kind = world ? "world" : "event";
        assertEquals(expected.size(), actual.size(), kind + " digest count");
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index), actual.get(index),
                    kind + " digest at index " + index + "\nfirst="
                            + expectedRun.snapshots().get(index) + "\nsecond="
                            + actualRun.snapshots().get(index));
        }
    }

    private static RunEvidence run(
            ArenaWorldFactory.ArenaSession arena, JsonNode transcript) {
        int ticks = transcript.path("ticks").intValue();
        var presses = transcript.path("presses");
        CanonicalWorldEncoder encoder = ArenaWorldFactory.canonicalEncoder();
        List<WorldDigest> worlds = new ArrayList<>();
        List<WorldDigest> events = new ArrayList<>();
        List<WorldSnapshot> snapshots = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            for (JsonNode press : presses) {
                if (press.path("tick").intValue() == tick) {
                    int key = key(press.path("key").textValue());
                    arena.input().keyDown(key);
                    arena.input().keyUp(key);
                }
            }
            var completed = arena.world().step();
            worlds.add(encoder.digest(completed.snapshot()));
            events.add(encoder.digestEvents(
                    completed.snapshot().tick(), completed.events()));
            snapshots.add(completed.snapshot());
        }
        return new RunEvidence(new TranscriptResult(worlds, events), snapshots);
    }

    private static int key(String value) {
        return switch (value) {
            case "W" -> Input.Keys.W;
            case "D" -> Input.Keys.D;
            case "SPACE" -> Input.Keys.SPACE;
            default -> throw new IllegalArgumentException("unsupported transcript key: " + value);
        };
    }

    private record RunEvidence(TranscriptResult result, List<WorldSnapshot> snapshots) {
    }
}
