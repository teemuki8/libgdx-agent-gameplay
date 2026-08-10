package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Records positive damage from a source to a subject. */
public record DamageApplied(EntityId subject, EntityId source, long amount)
        implements GameplayEvent {
    /** Validates event identities and positive damage. */
    public DamageApplied {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
        if (amount < 1) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-damage-event",
                    "amount >= 1",
                    Long.toString(amount),
                    "Record only positive applied damage.");
        }
    }
}
