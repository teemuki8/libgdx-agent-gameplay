package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Logical sprite asset/region plus explicit visual dimensions and normalized origin. */
public record Sprite(String asset, String region, Vec2 visualSize, Vec2 origin)
        implements Component {
    public static final ComponentType<Sprite> TYPE = new ComponentType<>("sprite", Sprite.class);

    /** Validates logical references, positive size, and normalized origin. */
    public Sprite {
        asset = IdentifierRules.requireLogicalAsset(asset, "sprite.asset");
        region = IdentifierRules.requireLogicalAsset(region, "sprite.region");
        Objects.requireNonNull(visualSize, "visualSize");
        Objects.requireNonNull(origin, "origin");
        if (visualSize.x() <= 0.0 || visualSize.y() <= 0.0
                || origin.x() < 0.0 || origin.x() > 1.0
                || origin.y() < 0.0 || origin.y() > 1.0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-sprite",
                    "positive visualSize and origin in [0,1]",
                    "size=" + visualSize + ",origin=" + origin,
                    "Use explicit positive dimensions and a normalized sprite origin.");
        }
    }
}
