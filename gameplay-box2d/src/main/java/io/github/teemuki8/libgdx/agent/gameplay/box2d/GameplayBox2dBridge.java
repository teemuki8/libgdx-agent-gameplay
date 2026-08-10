package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.MoveCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.LifecycleParticipant;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dAdapterLimits;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dContactLimits;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dContactPolicy;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dContacts;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dFixtureSpec;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dInspection;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dRegistration;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dUnitTransform;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dWorldSpec;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owner-thread mapping from gameplay entities to bridge-created Box2D bodies and evidence.
 *
 * <p>The caller owns the world, runtime, listener installation, and render loop. The bridge owns
 * only bodies and fixtures it creates and unregisters those objects before native destruction.</p>
 */
public final class GameplayBox2dBridge implements LifecycleParticipant, AutoCloseable {
    private static final String WORLD_ID = "gameplay";

    private final World world;
    private final Box2dBodyFactory bodyFactory;
    private final Box2dUnitConversion units;
    private final Box2dSolverSettings solver;
    private final AgentRuntime runtime;
    private final Thread ownerThread;
    private final Box2dInspection inspection;
    private final Box2dContacts runtimeContacts;
    private final Box2dContactCollector contacts;
    private final ContactListener runtimeContactListener;
    private final ContactListener evidenceListener;
    private final Map<EntityId, Box2dBodyHandle> bodies = new TreeMap<>();
    private final List<GameSystem> systems;
    private List<GameplayEvent> pendingContacts = List.of();
    private boolean captureRuntimeContacts;
    private boolean closed;

    /** Installs bounded inspection and fixed-phase systems without installing a world listener. */
    public GameplayBox2dBridge(
            World world,
            Box2dBodyFactory bodies,
            Box2dUnitConversion units,
            Box2dSolverSettings solver,
            AgentRuntime runtime,
            GameplayLimits limits) {
        this.world = Objects.requireNonNull(world, "world");
        bodyFactory = Objects.requireNonNull(bodies, "bodies");
        this.units = Objects.requireNonNull(units, "units");
        this.solver = Objects.requireNonNull(solver, "solver");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        GameplayLimits checkedLimits = Objects.requireNonNull(limits, "limits");
        if (!bodyFactory.units().equals(units)) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "matching bridge and body-factory unit conversions",
                    bodyFactory.units() + ":" + units,
                    "Construct both adapter layers with the same immutable conversion.");
        }
        ownerThread = Thread.currentThread();
        world.setWarmStarting(solver.warmStarting());
        world.setContinuousPhysics(solver.continuousPhysics());
        inspection = new Box2dInspection(runtime, Box2dAdapterLimits.developmentDefaults());
        inspection.registerWorld(WORLD_ID, world, new Box2dWorldSpec(
                solver.sleepingAllowed(), solver.warmStarting(), solver.continuousPhysics(),
                solver.velocityIterations(), solver.positionIterations(), OptionalDouble.empty(),
                new Box2dUnitTransform(units.renderUnitsPerMeter())));
        runtimeContacts = inspection.registerContacts(
                WORLD_ID, Box2dContactLimits.developmentDefaults(),
                new Box2dContactPolicy(true, true, false, false));
        contacts = new Box2dContactCollector(checkedLimits.maxEventsPerTick());
        runtimeContactListener = runtimeContacts.listener();
        evidenceListener = Box2dContactCollector.chain(
                contacts.listener(), new ConditionalRuntimeContactListener());
        systems = List.of(
                new PrePhysicsSystem(), new Box2dPhysicsSystem(this), new PostPhysicsSystem());
    }

    /** Returns the fixed PRE_PHYSICS, PHYSICS, and POST_PHYSICS systems. */
    public List<GameSystem> systems() {
        requireOwnerOpen("read-box2d-systems");
        return systems;
    }

    /** Returns evidence listener composition without installing it on the caller-owned world. */
    public ContactListener contactListener() {
        requireOwnerOpen("read-box2d-listener");
        return evidenceListener;
    }

    /** Returns evidence-first, application-second composition without installing it. */
    public ContactListener composeContactListener(ContactListener applicationListener) {
        requireOwnerOpen("compose-box2d-listener");
        return Box2dContactCollector.chain(
                evidenceListener,
                Objects.requireNonNull(applicationListener, "applicationListener"));
    }

    /** Resolves one live bridge-owned body mapping. */
    public Optional<Box2dBodyHandle> body(EntityId entityId) {
        requireOwnerOpen("read-box2d-body");
        return Optional.ofNullable(bodies.get(Objects.requireNonNull(entityId, "entityId")));
    }

    /** Returns the immutable unit conversion shared by physics and inspection. */
    public Box2dUnitConversion units() {
        return units;
    }

    @Override
    public int dependencyLevel() {
        return 100;
    }

    @Override
    public void onActivate(EntityView entity) {
        requireOwnerOpen("activate-box2d-body");
        if (entity.component(Transform2D.TYPE).isEmpty()
                || entity.component(Collider.TYPE).isEmpty()) {
            return;
        }
        int maximum = inspection.limits().bodies();
        if (bodies.size() >= maximum) {
            throw failure(GameplayDiagnosticCode.BOX2D_BODY_LIMIT_EXCEEDED,
                    "at most " + maximum + " inspected physics bodies",
                    Integer.toString(bodies.size() + 1),
                    "Despawn a physics entity before activating another.");
        }
        if (bodies.containsKey(entity.id())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "one native body per active entity",
                    entity.id().value(),
                    "Wait for the disposal barrier before reusing an entity ID.");
        }
        Box2dBodyHandle handle = bodyFactory.create(world, entity);
        handle.body().setSleepingAllowed(solver.sleepingAllowed());
        Box2dRegistration<Body> bodyRegistration = null;
        try {
            bodyRegistration = inspection.registerBody(
                    entity.id().value(), WORLD_ID, handle.body());
            Box2dRegistration<Fixture> fixtureRegistration = inspection.registerFixture(
                    handle.fixtureId(), entity.id().value(), handle.fixture(),
                    Box2dFixtureSpec.unspecified());
            handle.attachInspection(bodyRegistration, fixtureRegistration);
            bodies.put(entity.id(), handle);
        } catch (RuntimeException | Error failure) {
            if (bodyRegistration != null) {
                bodyRegistration.close();
            }
            world.destroyBody(handle.body());
            handle.markDisposed();
            throw failure;
        }
    }

    @Override
    public void onLogicalDespawn(EntityView entity) {
        requireOwnerOpen("logically-remove-box2d-body");
        Box2dBodyHandle handle = bodies.get(entity.id());
        if (handle != null) {
            handle.body().setActive(false);
        }
    }

    @Override
    public void onDispose(EntityId entityId) {
        requireOwnerOpen("dispose-box2d-body");
        Box2dBodyHandle handle = bodies.remove(entityId);
        if (handle != null) {
            Box2dNativeDisposal.destroy(world, handle);
        }
    }

    @Override
    public void onReset() {
        requireOwnerOpen("reset-box2d-bridge");
        contacts.reset();
        pendingContacts = List.of();
    }

    @Override
    public void onClose() {
        close();
    }

    /** Unregisters and destroys bridge-owned objects without disposing world or runtime. */
    @Override
    public void close() {
        requireOwner("close-box2d-bridge");
        if (closed) {
            return;
        }
        ArrayList<Box2dBodyHandle> remaining = new ArrayList<>(bodies.values());
        Collections.reverse(remaining);
        remaining.forEach(handle -> Box2dNativeDisposal.destroy(world, handle));
        bodies.clear();
        pendingContacts = List.of();
        contacts.reset();
        inspection.close();
        closed = true;
    }

    void stepPhysics(SystemContext context) {
        requireOwnerOpen("step-box2d-world");
        float seconds = seconds(context.fixedStepNanos());
        pendingContacts = contacts.captureStep(() -> {
            Runnable nativeStep = () -> world.step(
                    seconds, solver.velocityIterations(), solver.positionIterations());
            if (runtime.simulation().activeTick().isPresent()) {
                captureRuntimeContacts = true;
                try {
                    runtimeContacts.captureStep(nativeStep);
                } finally {
                    captureRuntimeContacts = false;
                }
            } else {
                nativeStep.run();
            }
        });
    }

    private void applyCommands(SystemContext context) {
        Map<EntityId, EntityView> movingEntities = context.query(Movement.TYPE).stream()
                .collect(Collectors.toUnmodifiableMap(EntityView::id, Function.identity()));
        for (var envelope : context.commands()) {
            if (!(envelope.command() instanceof MoveCommand move)) {
                continue;
            }
            Box2dBodyHandle handle = bodies.get(move.entityId());
            if (handle == null || handle.bodyType() == BodyDef.BodyType.StaticBody
                    || !handle.body().isActive()) {
                continue;
            }
            EntityView entity = movingEntities.get(move.entityId());
            if (entity == null) {
                continue;
            }
            double x = move.direction().x();
            double y = move.direction().y();
            double length = Math.hypot(x, y);
            if (length > 1.0) {
                x /= length;
                y /= length;
            }
            double speed = entity.component(Movement.TYPE).orElseThrow().maxSpeed();
            handle.body().setLinearVelocity(
                    units.toPhysicsFloat(x * speed, "command.velocity.x"),
                    units.toPhysicsFloat(y * speed, "command.velocity.y"));
        }
    }

    private void synchronizeAuthority(SystemContext context) {
        for (EntityView entity : context.query(Transform2D.TYPE, Collider.TYPE)) {
            Box2dBodyHandle handle = bodies.get(entity.id());
            if (handle == null || handle.bodyType() == BodyDef.BodyType.StaticBody
                    || !handle.body().isActive()) {
                continue;
            }
            Transform2D previous = entity.component(Transform2D.TYPE).orElseThrow();
            Vec2 position = units.toRenderUnits(
                    handle.body().getPosition().x, handle.body().getPosition().y);
            context.replace(entity.id(), Transform2D.TYPE, new Transform2D(
                    position, handle.body().getAngle(), previous.size(), previous.pivot()));
            entity.component(Movement.TYPE).ifPresent(previousMovement -> {
                Vec2 velocity = units.toRenderUnits(
                        handle.body().getLinearVelocity().x,
                        handle.body().getLinearVelocity().y);
                context.replace(entity.id(), Movement.TYPE,
                        new Movement(velocity, previousMovement.maxSpeed()));
            });
        }
        pendingContacts.forEach(context::emit);
        pendingContacts = List.of();
    }

    private void requireOwnerOpen(String operation) {
        requireOwner(operation);
        if (closed) {
            throw failure(GameplayDiagnosticCode.BOX2D_BRIDGE_CLOSED,
                    "open Box2D bridge",
                    "closed bridge",
                    "Create a new bridge rather than reusing a closed adapter.");
        }
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    operation,
                    "owner thread " + ownerThread.getName(),
                    Thread.currentThread().getName(),
                    "Run native registration and mutation on the bridge owner thread.");
        }
    }

    private static float seconds(long nanos) {
        float value = nanos / 1_000_000_000.0f;
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "positive float-representable fixed step",
                    Long.toString(nanos),
                    "Use a bounded positive GameWorld fixedStepNanos value.");
        }
        return value;
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code,
            String expected,
            String observed,
            String correction) {
        return GameplayException.validation(
                code, "operate-box2d-bridge", expected, observed, correction);
    }

    private final class PrePhysicsSystem implements GameSystem {
        private final SystemDescriptor descriptor = new SystemDescriptor(
                SystemId.of("box2d-apply-intent"), SystemPhase.PRE_PHYSICS, 10);

        @Override public SystemDescriptor descriptor() {
            return descriptor;
        }

        @Override public void update(SystemContext context) {
            applyCommands(context);
        }
    }

    private final class PostPhysicsSystem implements GameSystem {
        private final SystemDescriptor descriptor = new SystemDescriptor(
                SystemId.of("box2d-copy-authority"), SystemPhase.POST_PHYSICS, 10);

        @Override public SystemDescriptor descriptor() {
            return descriptor;
        }

        @Override public void update(SystemContext context) {
            synchronizeAuthority(context);
        }
    }

    private final class ConditionalRuntimeContactListener implements ContactListener {
        @Override public void beginContact(Contact contact) {
            if (captureRuntimeContacts) {
                runtimeContactListener.beginContact(contact);
            }
        }

        @Override public void endContact(Contact contact) {
            if (captureRuntimeContacts) {
                runtimeContactListener.endContact(contact);
            }
        }

        @Override public void preSolve(Contact contact, Manifold oldManifold) {
            if (captureRuntimeContacts) {
                runtimeContactListener.preSolve(contact, oldManifold);
            }
        }

        @Override public void postSolve(Contact contact, ContactImpulse impulse) {
            if (captureRuntimeContacts) {
                runtimeContactListener.postSolve(contact, impulse);
            }
        }
    }
}
