package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Locale;

/** Resettable deterministic allocator for semantic entity IDs. */
public final class EntityIdAllocator {
    private final String prefix;
    private final int width;
    private long sequence;

    /** Creates an allocator whose first ID has sequence one. */
    public EntityIdAllocator(String prefix, int width) {
        this.prefix = IdentifierRules.requireIdentifier(prefix, "entityIdPrefix");
        if (width < 1 || width > 18) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_IDENTIFIER,
                    "create-entity-id-allocator",
                    "decimal width in [1,18]",
                    Integer.toString(width),
                    "Choose a width such as 4 for projectile-0001.");
        }
        this.width = width;
    }

    /** Returns the next semantic ID. */
    public EntityId next() {
        if (sequence == Long.MAX_VALUE) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "allocate-entity-id",
                    "remaining sequence capacity",
                    Long.toString(sequence),
                    "Reset the world before the semantic sequence is exhausted.");
        }
        sequence++;
        String value = String.format(Locale.ROOT, "%s-%0" + width + "d", prefix, sequence);
        return EntityId.of(value);
    }

    /** Restarts the deterministic sequence at one. */
    public void reset() {
        sequence = 0;
    }
}
