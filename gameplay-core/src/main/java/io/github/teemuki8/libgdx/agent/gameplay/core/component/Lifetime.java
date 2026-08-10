package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Remaining deterministic simulation ticks before expiry. */
public record Lifetime(long remainingTicks) implements Component {
    public static final ComponentType<Lifetime> TYPE =
            new ComponentType<>("lifetime", Lifetime.class);

    /** Rejects a negative remaining lifetime. */
    public Lifetime {
        if (remainingTicks < 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-lifetime",
                    "remainingTicks >= 0",
                    Long.toString(remainingTicks),
                    "Use zero for an entity expiring at the current lifecycle barrier.");
        }
    }
}
