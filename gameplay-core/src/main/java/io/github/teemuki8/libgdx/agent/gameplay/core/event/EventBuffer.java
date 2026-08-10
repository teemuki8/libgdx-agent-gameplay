package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded event journal that requires one explicitly opened simulation tick. */
public final class EventBuffer {
    private final int limit;
    private final ArrayList<EventEnvelope> events = new ArrayList<>();
    private long openTick = -1;

    /** Creates an event journal using the world's per-tick limit. */
    public EventBuffer(GameplayLimits limits) {
        this.limit = Objects.requireNonNull(limits, "limits").maxEventsPerTick();
    }

    /** Opens one non-negative tick for event emission. */
    public void openTick(long tick) {
        if (openTick >= 0) {
            throw failure(GameplayDiagnosticCode.EVENT_TICK_ALREADY_OPEN,
                    "no open event tick", Long.toString(openTick),
                    "Close the current tick before opening another.");
        }
        if (tick < 0) {
            throw failure(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "tick >= 0", Long.toString(tick),
                    "Use the current non-negative simulation tick.");
        }
        events.clear();
        openTick = tick;
    }

    /** Emits one event without attributes. */
    public EventEnvelope emit(GameplayEvent event) {
        return emit(event, EventAttributes.empty());
    }

    /** Emits one event with bounded immutable attributes. */
    public EventEnvelope emit(GameplayEvent event, EventAttributes attributes) {
        if (openTick < 0) {
            throw failure(GameplayDiagnosticCode.EVENT_TICK_NOT_OPEN,
                    "an open simulation tick", "closed",
                    "Open the current tick before emitting gameplay evidence.");
        }
        if (events.size() >= limit) {
            throw failure(GameplayDiagnosticCode.EVENT_LIMIT_EXCEEDED,
                    "at most " + limit + " events", Integer.toString(events.size() + 1),
                    "Reduce event production before closing the tick.");
        }
        EventEnvelope envelope = new EventEnvelope(
                openTick, events.size(), event, attributes);
        events.add(envelope);
        return envelope;
    }

    /** Closes the current tick and returns an immutable event snapshot. */
    public List<EventEnvelope> closeTick() {
        if (openTick < 0) {
            throw failure(GameplayDiagnosticCode.EVENT_TICK_NOT_OPEN,
                    "an open simulation tick", "closed",
                    "Open a tick before attempting to close it.");
        }
        List<EventEnvelope> completed = List.copyOf(events);
        openTick = -1;
        return completed;
    }

    /** Returns immutable events staged in the currently open tick. */
    public List<EventEnvelope> currentEvents() {
        if (openTick < 0) {
            throw failure(GameplayDiagnosticCode.EVENT_TICK_NOT_OPEN,
                    "an open simulation tick", "closed",
                    "Read current events only from an active system callback.");
        }
        return List.copyOf(events);
    }

    /** Clears open and completed state at a world reset boundary. */
    public void reset() {
        events.clear();
        openTick = -1;
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code, String expected, String observed, String correction) {
        return GameplayException.validation(code, "record-event", expected, observed, correction);
    }
}
