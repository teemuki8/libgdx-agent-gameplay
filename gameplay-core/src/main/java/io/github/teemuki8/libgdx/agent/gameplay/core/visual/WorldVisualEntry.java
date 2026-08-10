package io.github.teemuki8.libgdx.agent.gameplay.core.visual;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Bounds2;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;
import java.util.Optional;

/** Immutable semantic visual evidence copied from one completed render model. */
public record WorldVisualEntry(
        EntityId entityId,
        String asset,
        String region,
        Vec2 worldPosition,
        Bounds2 spriteBounds,
        Optional<ScreenBounds> screenBounds,
        Vec2 pivot,
        double rotationRadians,
        boolean visible,
        boolean cameraVisible,
        String renderLayer,
        int renderOrder,
        VisualEvidenceStatus status) implements Comparable<WorldVisualEntry> {
    /** Validates and copies all evidence values. */
    public WorldVisualEntry {
        Objects.requireNonNull(entityId, "entityId");
        asset = IdentifierRules.requireLogicalAsset(asset, "visual.asset");
        region = IdentifierRules.requireLogicalAsset(region, "visual.region");
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(spriteBounds, "spriteBounds");
        Objects.requireNonNull(screenBounds, "screenBounds");
        Objects.requireNonNull(pivot, "pivot");
        renderLayer = IdentifierRules.requireIdentifier(renderLayer, "visual.renderLayer");
        Objects.requireNonNull(status, "status");
        if (!Double.isFinite(rotationRadians)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.UNPROJECTABLE_BOUNDS,
                    "create-visual-entry",
                    "finite visual rotation",
                    Double.toString(rotationRadians),
                    "Capture a validated finite transform.");
        }
    }

    @Override
    public int compareTo(WorldVisualEntry other) {
        return entityId.compareTo(other.entityId);
    }
}
