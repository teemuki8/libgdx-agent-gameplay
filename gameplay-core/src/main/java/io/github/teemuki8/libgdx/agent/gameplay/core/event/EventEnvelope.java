package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import java.util.Objects;

/** One event with deterministic tick-local sequence and bounded attributes. */
public record EventEnvelope(
        long tick,
        long sequence,
        GameplayEvent event,
        EventAttributes attributes) {
    /** Validates event envelope values. */
    public EventEnvelope {
        if (tick < 0 || sequence < 0) {
            throw new IllegalArgumentException("event tick and sequence must be non-negative");
        }
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(attributes, "attributes");
    }
}
