package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AssetResolverTest {
    @Test
    void resolvesTheAuthoritativeAnimationFrameAndFailsClosedWhenMissing() {
        AssetResolver resolver = new AssetResolver(new StubAtlas(Map.of()));
        Sprite sprite = new Sprite("player", "idle-0",
                new Vec2(1, 1), new Vec2(0.5, 0.5));
        Animation animation = new Animation(Map.of(
                "idle", new AnimationClip(List.of("idle-0", "idle-1"), 3, true)),
                "idle", 3, 1);

        assertEquals("idle-1", resolver.regionFor(sprite, animation));
        GameplayException failure = assertThrows(GameplayException.class,
                () -> resolver.resolve(new Sprite("missing", "missing",
                        new Vec2(1, 1), Vec2.ZERO), null, 7));
        assertEquals(GameplayDiagnosticCode.MISSING_ASSET, failure.code());
    }

    static final class StubAtlas extends TextureAtlas {
        private final Map<String, AtlasRegion> regions;

        StubAtlas(Map<String, AtlasRegion> regions) {
            this.regions = Map.copyOf(regions);
        }

        @Override
        public AtlasRegion findRegion(String name) {
            return regions.get(name);
        }
    }
}
