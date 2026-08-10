package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EventBufferTest {
    @Test
    void assignsTickLocalSequenceAndClosesToAnImmutableSnapshot() {
        EventBuffer buffer = new EventBuffer(GameplayLimits.defaults());
        EntityId player = EntityId.of("player");
        EntityId enemy = EntityId.of("enemy-primary");
        buffer.openTick(3);
        buffer.emit(new DamageApplied(enemy, player, 1), EventAttributes.of(Map.of(
                "contact", EventAttributeValue.string("contact-0001"))));
        buffer.emit(new EntityKilled(enemy, player));

        List<EventEnvelope> events = buffer.closeTick();

        assertEquals(List.of(0L, 1L), events.stream().map(EventEnvelope::sequence).toList());
        assertEquals(3, events.getFirst().tick());
        assertEquals("contact-0001",
                ((EventAttributeValue.StringValue) events.getFirst().attributes()
                        .values().get("contact")).value());
        assertThrows(UnsupportedOperationException.class, events::clear);
        assertCode(GameplayDiagnosticCode.EVENT_TICK_NOT_OPEN,
                () -> buffer.emit(new EntityKilled(enemy, player)));
    }

    @Test
    void copiesAndSortsAttributes() {
        HashMap<String, EventAttributeValue> source = new HashMap<>();
        source.put("zeta", EventAttributeValue.integer(2));
        source.put("alpha", EventAttributeValue.entity(EntityId.of("player")));
        EventAttributes attributes = EventAttributes.of(source);
        source.clear();

        assertEquals(List.of("alpha", "zeta"), attributes.values().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class,
                () -> attributes.values().put("later", EventAttributeValue.bool(true)));
    }

    @Test
    void enforcesTheConfiguredEventLimit() {
        EventBuffer buffer = new EventBuffer(limits(4_096, 1));
        EntityId player = EntityId.of("player");
        buffer.openTick(0);
        buffer.emit(new EntitySpawned(player));

        assertCode(GameplayDiagnosticCode.EVENT_LIMIT_EXCEEDED,
                () -> buffer.emit(new EntityDespawned(player)));
    }

    private static GameplayLimits limits(int commands, int events) {
        return new GameplayLimits(10_000, 64, 256, commands, 4_096, events,
                10_000, 4 * 1024 * 1024);
    }

    private static void assertCode(GameplayDiagnosticCode code, Runnable operation) {
        GameplayException failure = assertThrows(GameplayException.class, operation::run);
        assertEquals(code, failure.code());
    }
}
