package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionEnded;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Preallocated bounded copy-out queue for native begin/end contact callbacks. */
public final class Box2dContactCollector {
    private static final Comparator<ContactFact> ORDER = Comparator
            .comparing(ContactFact::firstFixtureId)
            .thenComparing(ContactFact::secondFixtureId)
            .thenComparing(ContactFact::phase);

    private final ContactFact[] records;
    private final ContactListener listener = new EvidenceListener();
    private boolean capturing;
    private int count;

    /** Creates a collector with an application-lowered per-step callback bound. */
    public Box2dContactCollector(int maxCallbacksPerStep) {
        if (maxCallbacksPerStep < 1
                || maxCallbacksPerStep > GameplayLimits.EVENTS_PER_TICK_MAXIMUM) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "configure-box2d-contacts",
                    "maxCallbacksPerStep in [1,"
                            + GameplayLimits.EVENTS_PER_TICK_MAXIMUM + "]",
                    Integer.toString(maxCallbacksPerStep),
                    "Choose a positive bound no greater than the gameplay event maximum.");
        }
        records = new ContactFact[maxCallbacksPerStep];
    }

    /** Returns the collector listener without installing it on the caller-owned world. */
    public ContactListener listener() {
        return listener;
    }

    /** Returns an evidence-first, application-second listener composition. */
    public ContactListener compose(ContactListener applicationListener) {
        return chain(listener, Objects.requireNonNull(applicationListener, "applicationListener"));
    }

    /** Runs one native step and returns sorted immutable gameplay contact events. */
    public List<GameplayEvent> captureStep(Runnable nativeStep) {
        Objects.requireNonNull(nativeStep, "nativeStep");
        if (capturing) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "capture-box2d-contacts",
                    "one active native step",
                    "nested capture",
                    "Complete the current Box2D step before starting another.");
        }
        count = 0;
        capturing = true;
        try {
            nativeStep.run();
            Arrays.sort(records, 0, count, ORDER);
            ArrayList<GameplayEvent> events = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ContactFact fact = records[index];
                events.add(fact.phase() == ContactPhase.STARTED
                        ? new CollisionStarted(fact.firstEntity(), fact.secondEntity(),
                                fact.firstFixtureId(), fact.secondFixtureId())
                        : new CollisionEnded(fact.firstEntity(), fact.secondEntity(),
                                fact.firstFixtureId(), fact.secondFixtureId()));
            }
            return List.copyOf(events);
        } finally {
            Arrays.fill(records, 0, count, null);
            count = 0;
            capturing = false;
        }
    }

    /** Clears copied facts at reset without retaining callback-owned native objects. */
    public void reset() {
        if (capturing) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "reset-box2d-contacts",
                    "no active native step",
                    "capture in progress",
                    "Reset only at the gameplay lifecycle barrier.");
        }
        Arrays.fill(records, null);
        count = 0;
    }

    static ContactListener chain(ContactListener first, ContactListener second) {
        return new ContactListener() {
            @Override public void beginContact(Contact contact) {
                first.beginContact(contact);
                second.beginContact(contact);
            }

            @Override public void endContact(Contact contact) {
                first.endContact(contact);
                second.endContact(contact);
            }

            @Override public void preSolve(Contact contact, Manifold oldManifold) {
                first.preSolve(contact, oldManifold);
                second.preSolve(contact, oldManifold);
            }

            @Override public void postSolve(Contact contact, ContactImpulse impulse) {
                first.postSolve(contact, impulse);
                second.postSolve(contact, impulse);
            }
        };
    }

    private void record(Contact contact, ContactPhase phase) {
        if (!capturing) {
            return;
        }
        Endpoint first = endpoint(contact.getFixtureA());
        Endpoint second = endpoint(contact.getFixtureB());
        if (first == null || second == null || first.fixtureId().equals(second.fixtureId())) {
            return;
        }
        if (count >= records.length) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_CONTACT_LIMIT_EXCEEDED,
                    "capture-box2d-contacts",
                    "at most " + records.length + " begin/end callbacks per native step",
                    Integer.toString(count + 1),
                    "Raise the application-lowered bound or reduce collision production.");
        }
        if (first.fixtureId().compareTo(second.fixtureId()) > 0) {
            Endpoint swap = first;
            first = second;
            second = swap;
        }
        records[count++] = new ContactFact(
                phase, first.entityId(), second.entityId(),
                first.fixtureId(), second.fixtureId());
    }

    private static Endpoint endpoint(Fixture fixture) {
        Object value = fixture.getUserData();
        if (value instanceof Box2dBodyFactory.FixtureIdentity identity) {
            return new Endpoint(identity.entityId(), identity.fixtureId());
        }
        return null;
    }

    private final class EvidenceListener implements ContactListener {
        @Override public void beginContact(Contact contact) {
            record(contact, ContactPhase.STARTED);
        }

        @Override public void endContact(Contact contact) {
            record(contact, ContactPhase.ENDED);
        }

        @Override public void preSolve(Contact contact, Manifold oldManifold) {
        }

        @Override public void postSolve(Contact contact, ContactImpulse impulse) {
        }
    }

    private enum ContactPhase {
        STARTED,
        ENDED
    }

    private record Endpoint(
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId entityId,
            String fixtureId) {
    }

    private record ContactFact(
            ContactPhase phase,
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId firstEntity,
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId secondEntity,
            String firstFixtureId,
            String secondFixtureId) {
    }
}
