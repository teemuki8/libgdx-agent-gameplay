package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import java.util.Objects;

/** Tick-targeted command plus stable source and source-local sequence. */
public record CommandEnvelope(
        long targetTick,
        CommandSourceId source,
        long sequence,
        GameplayCommand command) {
    /** Validates non-negative tick/sequence and immutable identities. */
    public CommandEnvelope {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(command, "command");
        if (targetTick < 0 || sequence < 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-command-envelope",
                    "targetTick and sequence >= 0",
                    "tick=" + targetTick + ",sequence=" + sequence,
                    "Use non-negative deterministic tick and sequence values.");
        }
    }
}
