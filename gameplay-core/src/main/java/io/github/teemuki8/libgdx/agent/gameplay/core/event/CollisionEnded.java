package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Objects;

/** Records the stable endpoints of one physics contact that ceased. */
public record CollisionEnded(
        EntityId first,
        EntityId second,
        String firstFixtureId,
        String secondFixtureId) implements GameplayEvent {
    /** Validates sorted entity endpoints and bounded fixture identities. */
    public CollisionEnded {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        firstFixtureId = IdentifierRules.requireIdentifier(firstFixtureId, "firstFixtureId");
        secondFixtureId = IdentifierRules.requireIdentifier(secondFixtureId, "secondFixtureId");
        if (firstFixtureId.compareTo(secondFixtureId) >= 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-collision-ended",
                    "strictly sorted distinct fixture IDs",
                    firstFixtureId + ":" + secondFixtureId,
                    "Sort collision endpoints by stable fixture ID before emitting.");
        }
    }
}
