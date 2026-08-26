package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Objects;

/** Records copied normal-impulse evidence for one solved physics contact callback. */
public record CollisionImpact(
        EntityId first,
        EntityId second,
        String firstFixtureId,
        String secondFixtureId,
        double normalImpulse) implements GameplayEvent {
    /** Validates sorted fixture endpoints and a finite positive copied impulse. */
    public CollisionImpact {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        firstFixtureId = IdentifierRules.requireIdentifier(firstFixtureId, "firstFixtureId");
        secondFixtureId = IdentifierRules.requireIdentifier(secondFixtureId, "secondFixtureId");
        if (firstFixtureId.compareTo(secondFixtureId) >= 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-collision-impact",
                    "strictly sorted distinct fixture IDs",
                    firstFixtureId + ":" + secondFixtureId,
                    "Sort collision endpoints by stable fixture ID before emitting.");
        }
        if (!Double.isFinite(normalImpulse) || normalImpulse <= 0.0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-collision-impact",
                    "finite positive normal impulse",
                    Double.toString(normalImpulse),
                    "Copy a positive normal impulse from the active physics callback.");
        }
    }
}
