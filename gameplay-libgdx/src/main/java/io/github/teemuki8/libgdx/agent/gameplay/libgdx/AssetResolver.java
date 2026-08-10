package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Map;
import java.util.Objects;

/** Explicit logical-region resolver over one application-owned texture atlas. */
public final class AssetResolver {
    private final TextureAtlas atlas;

    /** Wraps but does not own or dispose the supplied atlas. */
    public AssetResolver(TextureAtlas atlas) {
        this.atlas = Objects.requireNonNull(atlas, "atlas");
    }

    /** Resolves the frame already selected by authoritative animation state. */
    public ResolvedFrame resolve(Sprite sprite, Animation animation, long tick) {
        Objects.requireNonNull(sprite, "sprite");
        if (tick < 0) {
            throw failure(sprite.asset(), sprite.region(), tick,
                    "non-negative completed simulation tick");
        }
        String region = regionFor(sprite, animation);
        TextureAtlas.AtlasRegion resolved = atlas.findRegion(region);
        if (resolved == null) {
            throw failure(sprite.asset(), region, tick,
                    "region present in the application-owned atlas");
        }
        return new ResolvedFrame(sprite.asset(), region, resolved);
    }

    /** Configures caller-owned atlas textures for crisp nearest-neighbor fixture rendering. */
    public void useNearestFiltering() {
        atlas.getTextures().forEach(texture -> texture.setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest));
    }

    String regionFor(Sprite sprite, Animation animation) {
        if (animation == null) {
            return sprite.region();
        }
        AnimationClip clip = animation.clips().get(animation.currentClip());
        if (clip == null || animation.frameIndex() >= clip.frames().size()) {
            throw failure(sprite.asset(), animation.currentClip(), animation.elapsedTicks(),
                    "declared current animation clip and frame");
        }
        return clip.frames().get(animation.frameIndex());
    }

    private static GameplayException failure(
            String asset, String region, long tick, String expected) {
        return GameplayException.located(
                GameplayDiagnosticCode.MISSING_ASSET,
                "resolve-gameplay-asset",
                Map.of(
                        "asset", asset,
                        "region", region,
                        "tick", Long.toString(tick)),
                expected,
                asset + ":" + region,
                "Pack the logical region into the supplied atlas before rendering.");
    }
}
