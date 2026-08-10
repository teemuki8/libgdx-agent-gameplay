package io.github.teemuki8.libgdx.agent.gameplay.core.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.Objects;

/** Stable identity plus deterministic phase and numeric slot. */
public record SystemDescriptor(SystemId id, SystemPhase phase, int slot) {
    public static final int MAX_SLOT = 65_535;

    /** Validates identity, phase, and slot range. */
    public SystemDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phase, "phase");
        if (slot < 0 || slot > MAX_SLOT) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_SYSTEM_SLOT,
                    "declare-system",
                    "slot in [0,65535]",
                    Integer.toString(slot),
                    "Choose one explicit free slot in the system phase.");
        }
    }
}
