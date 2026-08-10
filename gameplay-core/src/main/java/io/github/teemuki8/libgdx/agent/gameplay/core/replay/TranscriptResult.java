package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.List;
import java.util.Objects;

/** Per-tick authoritative world and event digests from one completed replay. */
public record TranscriptResult(
        List<WorldDigest> tickDigests,
        List<WorldDigest> eventDigests) {
    /** Defensively copies equally sized digest sequences. */
    public TranscriptResult {
        Objects.requireNonNull(tickDigests, "tickDigests");
        Objects.requireNonNull(eventDigests, "eventDigests");
        tickDigests = List.copyOf(tickDigests);
        eventDigests = List.copyOf(eventDigests);
        if (tickDigests.size() != eventDigests.size()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_TRANSCRIPT,
                    "create-transcript-result",
                    "one event digest per world digest",
                    tickDigests.size() + ":" + eventDigests.size(),
                    "Retain both digests for every completed tick.");
        }
    }
}
