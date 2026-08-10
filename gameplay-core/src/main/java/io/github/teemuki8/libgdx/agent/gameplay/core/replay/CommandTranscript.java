package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable commands normalized by target tick, source ID, and source sequence. */
public record CommandTranscript(List<CommandEnvelope> commands) {
    private static final Comparator<CommandEnvelope> ORDER = Comparator
            .comparingLong(CommandEnvelope::targetTick)
            .thenComparing(CommandEnvelope::source)
            .thenComparingLong(CommandEnvelope::sequence);

    /** Copies, validates, and sorts the bounded transcript. */
    public CommandTranscript {
        Objects.requireNonNull(commands, "commands");
        if (commands.size() > GameplayLimits.QUEUED_COMMAND_MAXIMUM) {
            throw failure("at most " + GameplayLimits.QUEUED_COMMAND_MAXIMUM + " commands",
                    Integer.toString(commands.size()),
                    "Split the replay into bounded transcripts.");
        }
        commands = commands.stream().map(command -> Objects.requireNonNull(
                command, "command")).sorted(ORDER).toList();
        Set<SourceSequence> identities = new HashSet<>();
        for (CommandEnvelope command : commands) {
            if (!identities.add(new SourceSequence(command.source(), command.sequence()))) {
                throw failure("unique source and sequence",
                        command.source() + ":" + command.sequence(),
                        "Record each source-local command sequence exactly once.");
            }
        }
    }

    private static GameplayException failure(
            String expected, String observed, String correction) {
        return GameplayException.validation(
                GameplayDiagnosticCode.INVALID_TRANSCRIPT,
                "create-command-transcript", expected, observed, correction);
    }

    private record SourceSequence(CommandSourceId source, long sequence) {
    }
}
