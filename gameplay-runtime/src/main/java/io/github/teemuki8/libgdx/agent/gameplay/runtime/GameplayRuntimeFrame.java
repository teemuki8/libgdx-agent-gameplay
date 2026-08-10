package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Objects;

/** Immutable authoritative inputs captured together in {@code RUNTIME_CAPTURE}. */
public record GameplayRuntimeFrame(
        WorldSnapshot world,
        List<CommandEnvelope> commands,
        List<EventEnvelope> events,
        WorldVisualSnapshot visuals,
        String frameToken) {
    /** Validates tick correlation and defensively copies all evidence. */
    public GameplayRuntimeFrame {
        Objects.requireNonNull(world, "world");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(visuals, "visuals");
        if (world.tick() != visuals.tick()
                || events.stream().anyMatch(event -> event.tick() != world.tick())
                || frameToken == null || frameToken.isBlank() || frameToken.length() > 256) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.RUNTIME_FRAME_INCOMPLETE,
                    "create-gameplay-runtime-frame",
                    "matching world/visual/event tick and 1..256 character frame token",
                    world.tick() + ":" + visuals.tick() + ":" + String.valueOf(frameToken),
                    "Capture all immutable evidence from the same RUNTIME_CAPTURE phase.");
        }
    }
}
