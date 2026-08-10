package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardComponentsTest {
    @Test
    void acceptsFiniteImmutableStandardValues() {
        Transform2D transform = new Transform2D(
                new Vec2(2.0, 3.0), 0.0, new Vec2(0.8, 1.1), new Vec2(0.5, 0.5));
        Health health = new Health(30, 30);
        AnimationClip idle = new AnimationClip(List.of("idle-0", "idle-1"), 6, true);
        Animation animation = new Animation(Map.of("idle", idle), "idle", 12, 0);
        Render render = new Render("actors", 4, Rgba.WHITE, true);

        assertEquals(new Vec2(0.8, 1.1), transform.size());
        assertEquals(30, health.current());
        assertEquals(List.of("idle-0", "idle-1"),
                animation.clips().get("idle").frames());
        assertEquals("actors", render.layer());
    }

    @Test
    void rejectsNonFiniteOrOutOfRangeValuesWithTypedDiagnostics() {
        assertCode(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                () -> new Vec2(Double.NaN, 0.0));
        assertCode(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                () -> new Health(31, 30));
        assertCode(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                () -> new Transform2D(Vec2.ZERO, 0.0, new Vec2(1, 1),
                        new Vec2(1.1, 0.5)));
        assertCode(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                () -> new Collider(Collider.Shape.BOX, new Vec2(1, 0), Vec2.ZERO,
                        false, 1, 0xffff));
    }

    @Test
    void copiesAnimationCollections() {
        java.util.ArrayList<String> frames = new java.util.ArrayList<>(List.of("idle-0"));
        java.util.HashMap<String, AnimationClip> clips = new java.util.HashMap<>();
        clips.put("idle", new AnimationClip(frames, 4, true));
        Animation animation = new Animation(clips, "idle", 0, 0);

        frames.add("mutated");
        clips.clear();

        assertEquals(List.of("idle-0"), animation.clips().get("idle").frames());
        assertThrows(UnsupportedOperationException.class,
                () -> animation.clips().put("walk", animation.clips().get("idle")));
    }

    private static void assertCode(GameplayDiagnosticCode expected, Runnable operation) {
        GameplayException failure = assertThrows(GameplayException.class, operation::run);
        assertEquals(expected, failure.code());
    }
}
