package io.github.teemuki8.libgdx.agent.gameplay.core;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Hard limits for one gameplay world. Applications may lower but not raise them. */
public record GameplayLimits(
        int maxEntities,
        int maxComponentsPerEntity,
        int maxSystems,
        int maxQueuedCommands,
        int maxPendingMutations,
        int maxEventsPerTick,
        int maxVisualEntries,
        int maxSnapshotBytes) {
    public static final int ENTITY_MAXIMUM = 10_000;
    public static final int COMPONENTS_PER_ENTITY_MAXIMUM = 64;
    public static final int SYSTEM_MAXIMUM = 256;
    public static final int QUEUED_COMMAND_MAXIMUM = 4_096;
    public static final int PENDING_MUTATION_MAXIMUM = 4_096;
    public static final int EVENTS_PER_TICK_MAXIMUM = 4_096;
    public static final int VISUAL_ENTRY_MAXIMUM = 10_000;
    public static final int SNAPSHOT_BYTE_MAXIMUM = 4 * 1024 * 1024;

    /** Validates that every limit is positive and within the V1 maximum. */
    public GameplayLimits {
        within("maxEntities", maxEntities, ENTITY_MAXIMUM);
        within("maxComponentsPerEntity", maxComponentsPerEntity,
                COMPONENTS_PER_ENTITY_MAXIMUM);
        within("maxSystems", maxSystems, SYSTEM_MAXIMUM);
        within("maxQueuedCommands", maxQueuedCommands, QUEUED_COMMAND_MAXIMUM);
        within("maxPendingMutations", maxPendingMutations, PENDING_MUTATION_MAXIMUM);
        within("maxEventsPerTick", maxEventsPerTick, EVENTS_PER_TICK_MAXIMUM);
        within("maxVisualEntries", maxVisualEntries, VISUAL_ENTRY_MAXIMUM);
        within("maxSnapshotBytes", maxSnapshotBytes, SNAPSHOT_BYTE_MAXIMUM);
    }

    /** Returns the fixed V1 defaults. */
    public static GameplayLimits defaults() {
        return new GameplayLimits(
                ENTITY_MAXIMUM,
                COMPONENTS_PER_ENTITY_MAXIMUM,
                SYSTEM_MAXIMUM,
                QUEUED_COMMAND_MAXIMUM,
                PENDING_MUTATION_MAXIMUM,
                EVENTS_PER_TICK_MAXIMUM,
                VISUAL_ENTRY_MAXIMUM,
                SNAPSHOT_BYTE_MAXIMUM);
    }

    private static void within(String name, int value, int maximum) {
        if (value < 1 || value > maximum) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "configure-limits",
                    name + " in [1," + maximum + "]",
                    Integer.toString(value),
                    "Choose a positive value no greater than the V1 maximum.");
        }
    }
}
