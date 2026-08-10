package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntitySpawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionEnded;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CanonicalWorldEncoderTest {
    @Test
    void insertionOrderDoesNotAffectCanonicalDigest() {
        Map<ComponentType<?>, Component> forward = new LinkedHashMap<>();
        forward.put(Transform2D.TYPE, transform(2.0));
        forward.put(Health.TYPE, new Health(2, 3));
        Map<ComponentType<?>, Component> reverse = new LinkedHashMap<>();
        reverse.put(Health.TYPE, new Health(2, 3));
        reverse.put(Transform2D.TYPE, transform(2.0));

        WorldSnapshot left = new WorldSnapshot(7, List.of(
                entity("zeta", forward), entity("alpha", reverse)));
        WorldSnapshot right = new WorldSnapshot(7, List.of(
                entity("alpha", forward), entity("zeta", reverse)));

        assertEquals(CanonicalWorldEncoder.defaults().digest(left),
                CanonicalWorldEncoder.defaults().digest(right));
    }

    @Test
    void normalizesNegativeZeroButRetainsEveryAffectedValue() {
        WorldSnapshot positive = snapshot(transform(0.0));
        WorldSnapshot negative = snapshot(transform(-0.0));
        WorldSnapshot changed = snapshot(transform(0.25));

        assertEquals(CanonicalWorldEncoder.defaults().digest(positive),
                CanonicalWorldEncoder.defaults().digest(negative));
        assertNotEquals(CanonicalWorldEncoder.defaults().digest(positive),
                CanonicalWorldEncoder.defaults().digest(changed));
    }

    @Test
    void rejectsCanonicalOutputAboveConfiguredByteCap() {
        GameplayException failure = assertThrows(GameplayException.class,
                () -> new CanonicalWorldEncoder(16).digest(snapshot(transform(1.0))));

        assertEquals(GameplayDiagnosticCode.SNAPSHOT_LIMIT_EXCEEDED, failure.code());
    }

    @Test
    void eventSequenceAndTypedAttributeNamesHaveCanonicalOrder() {
        Map<String, EventAttributeValue> forward = new LinkedHashMap<>();
        forward.put("score", EventAttributeValue.integer(10));
        forward.put("impact", EventAttributeValue.decimal(-0.0));
        Map<String, EventAttributeValue> reverse = new LinkedHashMap<>();
        reverse.put("impact", EventAttributeValue.decimal(0.0));
        reverse.put("score", EventAttributeValue.integer(10));
        EventEnvelope zero = new EventEnvelope(5, 0,
                new EntitySpawned(EntityId.of("alpha")), EventAttributes.of(forward));
        EventEnvelope one = new EventEnvelope(5, 1,
                new EntitySpawned(EntityId.of("zeta")), EventAttributes.of(reverse));

        assertEquals(
                CanonicalWorldEncoder.defaults().digestEvents(5, List.of(zero, one)),
                CanonicalWorldEncoder.defaults().digestEvents(5, List.of(one, zero)));
    }

    @Test
    void collisionPhaseAndStableFixtureEndpointsAffectTheEventDigest() {
        EntityId alpha = EntityId.of("alpha");
        EntityId beta = EntityId.of("beta");
        EventEnvelope started = new EventEnvelope(4, 0,
                new CollisionStarted(alpha, beta, "alpha.collider", "beta.collider"),
                EventAttributes.empty());
        EventEnvelope ended = new EventEnvelope(4, 0,
                new CollisionEnded(alpha, beta, "alpha.collider", "beta.collider"),
                EventAttributes.empty());

        assertNotEquals(
                CanonicalWorldEncoder.defaults().digestEvents(4, List.of(started)),
                CanonicalWorldEncoder.defaults().digestEvents(4, List.of(ended)));
    }

    private static WorldSnapshot snapshot(Transform2D transform) {
        return new WorldSnapshot(2, List.of(entity("player", Map.of(
                Transform2D.TYPE, transform,
                Health.TYPE, new Health(3, 3)))));
    }

    private static EntitySnapshot entity(
            String id, Map<ComponentType<?>, Component> components) {
        return new EntitySnapshot(EntityId.of(id), EntityState.ACTIVE, components);
    }

    private static Transform2D transform(double rotation) {
        return new Transform2D(Vec2.ZERO, rotation, new Vec2(1, 1), new Vec2(0.5, 0.5));
    }
}
