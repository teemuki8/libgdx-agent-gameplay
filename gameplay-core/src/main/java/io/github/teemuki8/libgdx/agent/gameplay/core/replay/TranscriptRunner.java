package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.CompletedTick;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Replays a bounded transcript against one fresh owner-thread world. */
public final class TranscriptRunner {
    public static final long MAX_TICKS = 1_000_000;
    private final Supplier<GameWorld> worldFactory;
    private final CanonicalWorldEncoder encoder;

    /** Creates a runner using the default 4 MiB canonical record cap. */
    public TranscriptRunner(Supplier<GameWorld> worldFactory) {
        this(worldFactory, CanonicalWorldEncoder.defaults());
    }

    /** Creates a runner with an explicit canonical encoder. */
    public TranscriptRunner(
            Supplier<GameWorld> worldFactory, CanonicalWorldEncoder encoder) {
        this.worldFactory = Objects.requireNonNull(worldFactory, "worldFactory");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    /** Runs exactly {@code ticks} completed fixed ticks and returns paired digests. */
    public TranscriptResult run(CommandTranscript transcript, long ticks) {
        Objects.requireNonNull(transcript, "transcript");
        if (ticks < 0 || ticks > MAX_TICKS) {
            throw failure("ticks in [0," + MAX_TICKS + "]", Long.toString(ticks),
                    "Choose a bounded non-negative replay duration.");
        }
        List<CommandEnvelope> commands = transcript.commands();
        if (!commands.isEmpty() && commands.getLast().targetTick() >= ticks) {
            throw failure("every command targetTick < " + ticks,
                    Long.toString(commands.getLast().targetTick()),
                    "Increase the replay duration or remove out-of-range commands.");
        }
        ArrayList<WorldDigest> worlds = new ArrayList<>();
        ArrayList<WorldDigest> events = new ArrayList<>();
        try (GameWorld world = Objects.requireNonNull(
                worldFactory.get(), "worldFactory result")) {
            if (world.tick() != 0) {
                throw failure("fresh world at tick 0", Long.toString(world.tick()),
                        "Return a newly built world from the world factory.");
            }
            int commandIndex = 0;
            for (long tick = 0; tick < ticks; tick++) {
                while (commandIndex < commands.size()
                        && commands.get(commandIndex).targetTick() == tick) {
                    world.enqueue(commands.get(commandIndex));
                    commandIndex++;
                }
                CompletedTick completed = world.step();
                worlds.add(encoder.digest(completed.snapshot()));
                events.add(encoder.digestEvents(completed.snapshot().tick(),
                        completed.events()));
            }
        }
        return new TranscriptResult(worlds, events);
    }

    private static GameplayException failure(
            String expected, String observed, String correction) {
        return GameplayException.validation(
                GameplayDiagnosticCode.INVALID_TRANSCRIPT,
                "run-command-transcript", expected, observed, correction);
    }
}
