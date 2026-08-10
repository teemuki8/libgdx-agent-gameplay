package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Objects;

/** Records completion of one named objective by an entity. */
public record ObjectiveCompleted(EntityId subject, String objectiveId) implements GameplayEvent {
    /** Validates event identity and objective ID. */
    public ObjectiveCompleted {
        Objects.requireNonNull(subject, "subject");
        objectiveId = IdentifierRules.requireIdentifier(objectiveId, "objectiveId");
    }
}
