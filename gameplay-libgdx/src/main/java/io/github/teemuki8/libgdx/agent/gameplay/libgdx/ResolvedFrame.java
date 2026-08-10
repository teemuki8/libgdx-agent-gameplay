package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Objects;

/** One resolved logical sprite frame backed by an application-owned atlas region. */
public record ResolvedFrame(
        String asset,
        String region,
        TextureAtlas.AtlasRegion textureRegion) {
    /** Validates logical identity and native reference presence. */
    public ResolvedFrame {
        asset = IdentifierRules.requireLogicalAsset(asset, "resolvedFrame.asset");
        region = IdentifierRules.requireLogicalAsset(region, "resolvedFrame.region");
        Objects.requireNonNull(textureRegion, "textureRegion");
    }
}
