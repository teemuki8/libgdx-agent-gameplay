package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import java.util.List;

/** Immutable commands, events, and world state from one completed simulation tick. */
public record CompletedTick(
        WorldSnapshot snapshot,
        List<CommandEnvelope> commands,
        List<EventEnvelope> events) {
    /** Defensively copies command and event evidence. */
    public CompletedTick {
        commands = List.copyOf(commands);
        events = List.copyOf(events);
    }
}
