package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventCodecRegistry;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CustomEventCodecTest {
    @Test
    void defaultRemainsClosedAndCustomFieldsCannotOverrideEnvelopeEvidence() {
        assertThrows(io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException.class,
                () -> new CanonicalWorldEncoder(4096).digestEvents(0, List.of(event(4))));
        EventCodecRegistry codecs = EventCodecRegistry.builder().register("harvested", Harvested.class,
                event -> new EventCodecRegistry.Payload(Optional.empty(), Optional.empty(),
                        EventAttributes.of(Map.of("amount", EventAttributeValue.integer(event.amount()))))).build();
        CanonicalWorldEncoder encoder = new CanonicalWorldEncoder(4096, StandardComponents.registry(), codecs);
        assertThrows(io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException.class,
                () -> encoder.digestEvents(0, List.of(new EventEnvelope(0, 0, new Harvested(4),
                        EventAttributes.of(Map.of("amount", EventAttributeValue.integer(99)))))));
        assertThrows(io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException.class,
                () -> EventCodecRegistry.builder().register("damage-applied", Harvested.class,
                        event -> new EventCodecRegistry.Payload(Optional.empty(), Optional.empty(), EventAttributes.empty())));
    }

    @Test
    void customPayloadParticipatesInCanonicalEventDigest() {
        EventCodecRegistry codecs = EventCodecRegistry.builder().register("harvested", Harvested.class,
                event -> new EventCodecRegistry.Payload(Optional.empty(), Optional.empty(),
                        EventAttributes.of(Map.of("amount", EventAttributeValue.integer(event.amount()))))).build();
        CanonicalWorldEncoder encoder = new CanonicalWorldEncoder(4096, StandardComponents.registry(), codecs);
        assertNotEquals(encoder.digestEvents(0, List.of(event(4))),
                encoder.digestEvents(0, List.of(event(5))));
    }

    private static EventEnvelope event(long value) {
        return new EventEnvelope(0, 0, new Harvested(value), EventAttributes.empty());
    }

    private record Harvested(long amount) implements GameplayEvent {}
}
