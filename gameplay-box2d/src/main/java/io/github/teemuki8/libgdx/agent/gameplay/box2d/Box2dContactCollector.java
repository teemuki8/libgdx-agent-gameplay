package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.structs.b2ContactData;
import com.badlogic.gdx.box2d.structs.b2ContactEvents;
import com.badlogic.gdx.box2d.structs.b2Manifold;
import com.badlogic.gdx.box2d.structs.b2ManifoldPoint;
import com.badlogic.gdx.box2d.structs.b2ShapeId;
import com.badlogic.gdx.box2d.structs.b2WorldId;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionEnded;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionImpact;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reusable bounded copy-out collector for Box2D 3 post-step event arrays. */
public final class Box2dContactCollector implements AutoCloseable {
    private static final Comparator<ContactFact> ORDER = Comparator
            .comparing(ContactFact::firstFixtureId)
            .thenComparing(ContactFact::secondFixtureId)
            .thenComparing(ContactFact::phase);

    private final ContactFact[] records;
    private final b2ContactEvents events = new b2ContactEvents();
    private final b2ContactData.b2ContactDataPointer contactData;
    private boolean capturing;
    private boolean closed;
    private int count;

    /** Creates native scratch and copied fact storage at the configured event bound. */
    public Box2dContactCollector(int maxEventsPerStep) {
        if (maxEventsPerStep < 1
                || maxEventsPerStep > GameplayLimits.EVENTS_PER_TICK_MAXIMUM) {
            throw new IllegalArgumentException("maxEventsPerStep is outside gameplay limits");
        }
        records = new ContactFact[maxEventsPerStep];
        contactData = new b2ContactData.b2ContactDataPointer(maxEventsPerStep, false);
    }

    List<GameplayEvent> captureStep(b2WorldId worldId,
            Map<Box2dBodyHandle.ShapeKey, Endpoint> endpoints, Runnable nativeStep) {
        requireOpen();
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(nativeStep, "nativeStep");
        if (capturing) {
            throw failure("one active native step", "nested capture");
        }
        count = 0;
        capturing = true;
        try {
            nativeStep.run();
            Box2d.b2World_GetContactEvents(worldId, events);
            for (int index = 0; index < events.beginCount(); index++) {
                var event = events.beginEvents().asStackElement(index);
                record(event.shapeIdA(), event.shapeIdB(), Phase.STARTED, 0.0, endpoints);
            }
            for (int index = 0; index < events.hitCount(); index++) {
                var event = events.hitEvents().asStackElement(index);
                double impulse = totalNormalImpulse(event.shapeIdA(), event.shapeIdB(), endpoints);
                if (impulse > 0.0) {
                    record(event.shapeIdA(), event.shapeIdB(), Phase.IMPACT, impulse, endpoints);
                }
            }
            for (int index = 0; index < events.endCount(); index++) {
                var event = events.endEvents().asStackElement(index);
                record(event.shapeIdA(), event.shapeIdB(), Phase.ENDED, 0.0, endpoints);
            }
            Arrays.sort(records, 0, count, ORDER);
            ArrayList<GameplayEvent> result = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ContactFact fact = records[index];
                result.add(switch (fact.phase()) {
                    case STARTED -> new CollisionStarted(fact.firstEntity(), fact.secondEntity(),
                            fact.firstFixtureId(), fact.secondFixtureId());
                    case IMPACT -> new CollisionImpact(fact.firstEntity(), fact.secondEntity(),
                            fact.firstFixtureId(), fact.secondFixtureId(), fact.normalImpulse());
                    case ENDED -> new CollisionEnded(fact.firstEntity(), fact.secondEntity(),
                            fact.firstFixtureId(), fact.secondFixtureId());
                });
            }
            return List.copyOf(result);
        } finally {
            Arrays.fill(records, 0, count, null);
            count = 0;
            capturing = false;
        }
    }

    private double totalNormalImpulse(b2ShapeId first, b2ShapeId second,
            Map<Box2dBodyHandle.ShapeKey, Endpoint> endpoints) {
        if (!knownPair(first, second, endpoints)) {
            return 0.0;
        }
        int capacity = Box2d.b2Shape_GetContactCapacity(first);
        if (capacity > records.length) {
            throw failure("contact-data capacity at most " + records.length,
                    Integer.toString(capacity));
        }
        if (capacity <= 0) {
            return 0.0;
        }
        int copied = Box2d.b2Shape_GetContactData(first, contactData, capacity);
        Box2dBodyHandle.ShapeKey expectedFirst = Box2dBodyHandle.ShapeKey.copyOf(first);
        Box2dBodyHandle.ShapeKey expectedSecond = Box2dBodyHandle.ShapeKey.copyOf(second);
        double maximum = 0.0;
        for (int contactIndex = 0; contactIndex < copied; contactIndex++) {
            b2ContactData contact = contactData.asStackElement(contactIndex);
            Box2dBodyHandle.ShapeKey actualFirst =
                    Box2dBodyHandle.ShapeKey.copyOf(contact.shapeIdA());
            Box2dBodyHandle.ShapeKey actualSecond =
                    Box2dBodyHandle.ShapeKey.copyOf(contact.shapeIdB());
            if (!samePair(expectedFirst, expectedSecond, actualFirst, actualSecond)) {
                continue;
            }
            b2Manifold manifold = contact.manifold();
            for (int pointIndex = 0; pointIndex < manifold.pointCount(); pointIndex++) {
                b2ManifoldPoint point = manifold.points().asStackElement(pointIndex);
                maximum = Math.max(maximum, point.totalNormalImpulse());
            }
        }
        return maximum;
    }

    private void record(b2ShapeId firstShape, b2ShapeId secondShape, Phase phase,
            double impulse, Map<Box2dBodyHandle.ShapeKey, Endpoint> endpoints) {
        Endpoint first = endpoints.get(Box2dBodyHandle.ShapeKey.copyOf(firstShape));
        Endpoint second = endpoints.get(Box2dBodyHandle.ShapeKey.copyOf(secondShape));
        if (first == null || second == null || first.fixtureId().equals(second.fixtureId())) {
            return;
        }
        if (count >= records.length) {
            throw failure("at most " + records.length + " copied contact events",
                    Integer.toString(count + 1));
        }
        if (first.fixtureId().compareTo(second.fixtureId()) > 0) {
            Endpoint swap = first;
            first = second;
            second = swap;
        }
        records[count++] = new ContactFact(phase, first.entityId(), second.entityId(),
                first.fixtureId(), second.fixtureId(), impulse);
    }

    private static boolean knownPair(b2ShapeId first, b2ShapeId second,
            Map<Box2dBodyHandle.ShapeKey, Endpoint> endpoints) {
        return endpoints.containsKey(Box2dBodyHandle.ShapeKey.copyOf(first))
                && endpoints.containsKey(Box2dBodyHandle.ShapeKey.copyOf(second));
    }

    private static boolean samePair(Box2dBodyHandle.ShapeKey first,
            Box2dBodyHandle.ShapeKey second, Box2dBodyHandle.ShapeKey actualFirst,
            Box2dBodyHandle.ShapeKey actualSecond) {
        return first.equals(actualFirst) && second.equals(actualSecond)
                || first.equals(actualSecond) && second.equals(actualFirst);
    }

    /** Clears copied facts at a lifecycle barrier. */
    public void reset() {
        requireOpen();
        if (capturing) {
            throw failure("no active capture", "capture in progress");
        }
        Arrays.fill(records, null);
        count = 0;
    }

    /** Releases the one owned native contact-data buffer. */
    @Override public void close() {
        if (closed) return;
        if (capturing) throw failure("no active capture", "capture in progress");
        contactData.free();
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Box2dContactCollector is closed");
    }

    private static GameplayException failure(String expected, String observed) {
        return GameplayException.validation(GameplayDiagnosticCode.BOX2D_CONTACT_LIMIT_EXCEEDED,
                "capture-box2d3-contacts", expected, observed,
                "Raise the configured bound or reduce contact production.");
    }

    record Endpoint(EntityId entityId, String fixtureId) {
        Endpoint {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(fixtureId, "fixtureId");
        }
    }

    private enum Phase { STARTED, IMPACT, ENDED }

    private record ContactFact(Phase phase, EntityId firstEntity, EntityId secondEntity,
            String firstFixtureId, String secondFixtureId, double normalImpulse) {
    }
}
