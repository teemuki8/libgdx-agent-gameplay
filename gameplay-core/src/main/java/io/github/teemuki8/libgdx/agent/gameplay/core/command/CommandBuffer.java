package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded future command queue with deterministic source/sequence ordering. */
public final class CommandBuffer {
    public static final long MAX_FUTURE_TICKS = 4_096;
    private static final Comparator<CommandEnvelope> ORDER = Comparator
            .comparing(CommandEnvelope::source)
            .thenComparingLong(CommandEnvelope::sequence);

    private final int limit;
    private final Map<Long, List<CommandEnvelope>> byTick = new HashMap<>();
    private final Set<SourceSequence> queuedSequences = new HashSet<>();
    private final Map<CommandSourceId, Long> consumedSequence = new HashMap<>();
    private long currentTick;
    private int size;

    /** Creates a command queue with the world's command limit. */
    public CommandBuffer(GameplayLimits limits) {
        this.limit = limits.maxQueuedCommands();
    }

    /** Adds one command after validating time, identity, and capacity. */
    public void enqueue(CommandEnvelope envelope) {
        if (envelope.targetTick() < currentTick) {
            throw failure(GameplayDiagnosticCode.LATE_COMMAND, "targetTick >= " + currentTick,
                    Long.toString(envelope.targetTick()),
                    "Retarget the command to the current or a future tick.");
        }
        if (envelope.targetTick() > currentTick + MAX_FUTURE_TICKS) {
            throw failure(GameplayDiagnosticCode.COMMAND_WINDOW_EXCEEDED,
                    "targetTick <= " + (currentTick + MAX_FUTURE_TICKS),
                    Long.toString(envelope.targetTick()),
                    "Queue intent only within the retained future window.");
        }
        long watermark = consumedSequence.getOrDefault(envelope.source(), -1L);
        if (envelope.sequence() <= watermark) {
            throw failure(GameplayDiagnosticCode.NON_MONOTONIC_COMMAND_SEQUENCE,
                    "sequence > " + watermark, Long.toString(envelope.sequence()),
                    "Continue the source's monotonic sequence after consumed commands.");
        }
        byTick.values().stream()
                .flatMap(List::stream)
                .filter(queued -> queued.source().equals(envelope.source()))
                .filter(queued -> sequenceRunsBackward(envelope, queued))
                .findFirst()
                .ifPresent(queued -> {
                    throw failure(GameplayDiagnosticCode.NON_MONOTONIC_COMMAND_SEQUENCE,
                            "source sequence order matching target tick order",
                            envelope.sequence() + "@" + envelope.targetTick()
                                    + " versus " + queued.sequence() + "@" + queued.targetTick(),
                            "Keep later source sequences on the same or a later target tick.");
                });
        SourceSequence key = new SourceSequence(envelope.source(), envelope.sequence());
        if (!queuedSequences.add(key)) {
            throw failure(GameplayDiagnosticCode.DUPLICATE_COMMAND_SEQUENCE,
                    "unique source and sequence", key.toString(),
                    "Increment the source-local command sequence exactly once.");
        }
        if (size >= limit) {
            queuedSequences.remove(key);
            throw failure(GameplayDiagnosticCode.COMMAND_LIMIT_EXCEEDED,
                    "at most " + limit + " queued commands", Integer.toString(size + 1),
                    "Advance the world or lower command production before retrying.");
        }
        byTick.computeIfAbsent(envelope.targetTick(), ignored -> new ArrayList<>()).add(envelope);
        size++;
    }

    /** Returns an immutable deterministically ordered view for one tick. */
    public List<CommandEnvelope> commandsFor(long tick) {
        List<CommandEnvelope> commands = byTick.get(tick);
        if (commands == null) {
            return List.of();
        }
        return commands.stream().sorted(ORDER).toList();
    }

    /** Discards older ticks and advances the late-command boundary. */
    public void advanceTo(long tick) {
        if (tick < currentTick) {
            throw failure(GameplayDiagnosticCode.LATE_COMMAND,
                    "tick >= " + currentTick, Long.toString(tick),
                    "Advance command time monotonically.");
        }
        ArrayList<Long> expiredTicks = new ArrayList<>();
        byTick.forEach((target, commands) -> {
            if (target < tick) {
                expiredTicks.add(target);
                commands.forEach(command -> {
                    consumedSequence.merge(command.source(), command.sequence(), Math::max);
                    queuedSequences.remove(new SourceSequence(
                            command.source(), command.sequence()));
                });
                size -= commands.size();
            }
        });
        expiredTicks.forEach(byTick::remove);
        currentTick = tick;
    }

    /** Clears all commands and sequence watermarks at a reset boundary. */
    public void reset() {
        byTick.clear();
        queuedSequences.clear();
        consumedSequence.clear();
        currentTick = 0;
        size = 0;
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code, String expected, String observed, String correction) {
        return GameplayException.validation(code, "enqueue-command", expected, observed, correction);
    }

    private static boolean sequenceRunsBackward(
            CommandEnvelope candidate, CommandEnvelope queued) {
        return candidate.sequence() < queued.sequence()
                && candidate.targetTick() > queued.targetTick()
                || candidate.sequence() > queued.sequence()
                && candidate.targetTick() < queued.targetTick();
    }

    private record SourceSequence(CommandSourceId source, long sequence) {
    }
}
