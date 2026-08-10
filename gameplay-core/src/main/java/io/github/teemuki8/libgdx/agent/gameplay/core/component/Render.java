package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import java.util.Objects;

/** Stable render layer/order plus tint and visibility. */
public record Render(String layer, int order, Rgba tint, boolean visible) implements Component {
    public static final ComponentType<Render> TYPE = new ComponentType<>("render", Render.class);

    /** Validates the layer and tint. */
    public Render {
        layer = IdentifierRules.requireIdentifier(layer, "render.layer");
        Objects.requireNonNull(tint, "tint");
    }
}
