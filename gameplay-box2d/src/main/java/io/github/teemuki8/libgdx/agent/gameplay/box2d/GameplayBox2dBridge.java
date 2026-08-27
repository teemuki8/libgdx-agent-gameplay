package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
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
    private final Map<Box2dJointId, OwnedRevoluteJoint> joints = new TreeMap<>();
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

    /** Returns copied stable state without exposing native Box2D identity. */
    public Optional<Box2dBodyState> bodyState(EntityId entityId) {
        requireOwnerOpen("read-box2d-body-state");
        Box2dBodyHandle handle = bodies.get(Objects.requireNonNull(entityId, "entityId"));
        return handle == null ? Optional.empty() : Optional.of(handle.state(units));
    }

    /**
     * Creates and inspects one bounded revolute joint using copied stable IDs and values.
     *
     * @param spec immutable joint specification in render units and radians
     */
    public void createRevoluteJoint(Box2dRevoluteJointSpec spec) {
        requireOwnerOpen("create-revolute-joint");
        Box2dRevoluteJointSpec checked = Objects.requireNonNull(spec, "spec");
        requireWorldUnlocked("create-revolute-joint");
        if (joints.containsKey(checked.id())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "unique bridge-owned joint ID", checked.id().value(),
                    "Remove the existing joint before reusing its stable ID.");
        }
        int maximum = inspection.limits().joints();
        if (joints.size() >= maximum) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "at most " + maximum + " inspected physics joints",
                    Integer.toString(joints.size() + 1),
                    "Remove a bridge-owned joint before creating another.");
        }
        Box2dBodyHandle first = requireDynamicJointEndpoint(checked.first());
        Box2dBodyHandle second = requireDynamicJointEndpoint(checked.second());
        if (first == second) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "distinct native joint endpoints", checked.first().value(),
                    "Supply two distinct mapped dynamic bodies.");
        }

        RevoluteJointDef definition = new RevoluteJointDef();
        definition.initialize(first.body(), second.body(), new Vector2(
                units.toPhysicsFloat(
                        checked.anchorRenderUnits().x(), "joint.anchorRenderUnits.x"),
                units.toPhysicsFloat(
                        checked.anchorRenderUnits().y(), "joint.anchorRenderUnits.y")));
        definition.enableLimit = true;
        definition.lowerAngle = finiteFloat(
                checked.lowerAngleRadians(), "joint.lowerAngleRadians");
        definition.upperAngle = finiteFloat(
                checked.upperAngleRadians(), "joint.upperAngleRadians");
        definition.collideConnected = checked.collideConnected();

        Joint nativeJoint = world.createJoint(definition);
        if (!(nativeJoint instanceof RevoluteJoint revoluteJoint)) {
            world.destroyJoint(nativeJoint);
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "native revolute joint", nativeJoint.getClass().getName(),
                    "Use the bridge revolute-joint creation path with the supported binding.");
        }
        Box2dRegistration<Joint> registration = null;
        try {
            registration = inspection.registerJoint(
                    checked.id().value(), WORLD_ID, nativeJoint);
            joints.put(checked.id(), new OwnedRevoluteJoint(
                    checked.id(), checked.first(), checked.second(),
                    revoluteJoint, registration));
        } catch (RuntimeException | Error failure) {
            if (registration != null) {
                registration.close();
            }
            world.destroyJoint(nativeJoint);
            throw failure;
        }
    }

    /**
     * Configures the motor of one bridge-owned revolute joint.
     *
     * @param id stable joint ID
     * @param motor immutable motor configuration
     */
    public void configureRevoluteMotor(Box2dJointId id, Box2dRevoluteMotor motor) {
        requireOwnerOpen("configure-revolute-motor");
        requireWorldUnlocked("configure-revolute-motor");
        RevoluteJoint joint = requireRevoluteJoint(id);
        Box2dRevoluteMotor checked = Objects.requireNonNull(motor, "motor");
        float speed = finiteFloat(
                checked.speedRadiansPerSecond(), "motor.speedRadiansPerSecond");
        float torque = finiteFloat(
                checked.maximumTorqueNewtonMetres(), "motor.maximumTorqueNewtonMetres");
        joint.setMotorSpeed(speed);
        joint.setMaxMotorTorque(torque);
        joint.enableMotor(checked.enabled());
    }

    /**
     * Applies a copied Box2D SI force at a mapped non-static body's centre and wakes it.
     *
     * @param entityId mapped gameplay entity
     * @param forceNewtons force in newtons, without render-unit conversion
     */
    public void applyForceToCenter(EntityId entityId, Vec2 forceNewtons) {
        requireOwnerOpen("apply-force-to-center");
        requireWorldUnlocked("apply-force-to-center");
        Box2dBodyHandle handle = requireHandle(entityId);
        Vec2 checked = Objects.requireNonNull(forceNewtons, "forceNewtons");
        if (handle.bodyType() == BodyDef.BodyType.StaticBody || !handle.body().isActive()) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "active non-static mapped body", entityId.value(),
                    "Apply force only to an active dynamic or kinematic mapped body.");
        }
        handle.body().applyForceToCenter(
                finiteFloat(checked.x(), "forceNewtons.x"),
                finiteFloat(checked.y(), "forceNewtons.y"), true);
    }

    /**
     * Applies one copied Box2D SI torque to a mapped dynamic body and wakes it.
     *
     * @param entityId mapped gameplay entity
     * @param torqueNewtonMetres torque in newton-metres, without render-unit conversion
     */
    public void applyTorque(EntityId entityId, double torqueNewtonMetres) {
        requireOwnerOpen("apply-box2d-torque");
        requireWorldUnlocked("apply-box2d-torque");
        Box2dBodyHandle handle =
                requireActiveDynamicBody(entityId, "apply-box2d-torque");
        float torque = finiteFloat(torqueNewtonMetres, "torqueNewtonMetres");
        handle.body().applyTorque(torque, true);
    }

    /**
     * Returns copied revolute-joint state without exposing native identity.
     *
     * @param id stable joint ID
     * @return copied state, or empty when the ID is not bridge-owned
     */
    public Optional<Box2dRevoluteJointState> revoluteJointState(Box2dJointId id) {
        requireOwnerOpen("read-revolute-joint-state");
        Box2dJointId checked = Objects.requireNonNull(id, "id");
        OwnedRevoluteJoint owned = joints.get(checked);
        if (owned == null) {
            return Optional.empty();
        }
        RevoluteJoint joint = checkedRevoluteJoint(owned);
        return Optional.of(new Box2dRevoluteJointState(
                owned.id(), owned.first(), owned.second(),
                joint.getJointAngle(), joint.getJointSpeed(), joint.isMotorEnabled(),
                joint.getMotorSpeed(), joint.getMaxMotorTorque()));
    }

    /**
     * Removes one bridge-owned joint; an absent ID is already removed.
     *
     * @param id stable joint ID
     */
    public void removeJoint(Box2dJointId id) {
        requireOwnerOpen("remove-box2d-joint");
        requireWorldUnlocked("remove-box2d-joint");
        OwnedRevoluteJoint owned = joints.remove(Objects.requireNonNull(id, "id"));
        if (owned != null) {
            destroyJoint(owned);
        }
    }

    /** Deactivates one mapped body at an authoritative gameplay transition. */
    public void deactivate(EntityId entityId) {
        requireOwnerOpen("deactivate-box2d-body");
        requireHandle(entityId).body().setActive(false);
    }

    /** Stops one mapped body without exposing its native identity. */
    public void stop(EntityId entityId) {
        requireOwnerOpen("stop-box2d-body");
        requireHandle(entityId).body().setLinearVelocity(0.0f, 0.0f);
    }

    Optional<Box2dBodyHandle> handle(EntityId entityId) {
        requireOwnerOpen("read-internal-box2d-handle");
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
        destroyConnectedJoints(Objects.requireNonNull(entityId, "entityId"));
        Box2dBodyHandle handle = bodies.remove(entityId);
        if (handle != null) {
            Box2dNativeDisposal.destroy(world, handle);
        }
    }

    @Override
    public void onReset() {
        requireOwnerOpen("reset-box2d-bridge");
        destroyAllJoints();
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
        requireWorldUnlocked("close-box2d-bridge");
        destroyAllJoints();
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

    private Box2dBodyHandle requireDynamicJointEndpoint(EntityId entityId) {
        Box2dBodyHandle handle = requireHandle(entityId);
        if (handle.bodyType() == BodyDef.BodyType.StaticBody) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "non-static mapped joint endpoint", entityId.value(),
                    "Join only dynamic or kinematic bridge-owned bodies.");
        }
        return handle;
    }

    private RevoluteJoint requireRevoluteJoint(Box2dJointId id) {
        Box2dJointId checked = Objects.requireNonNull(id, "id");
        OwnedRevoluteJoint owned = joints.get(checked);
        if (owned == null) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "bridge-owned revolute joint", checked.value(),
                    "Create the stable joint ID before configuring its motor.");
        }
        return checkedRevoluteJoint(owned);
    }

    private RevoluteJoint checkedRevoluteJoint(OwnedRevoluteJoint owned) {
        Joint joint = owned.joint();
        if (!(joint instanceof RevoluteJoint revoluteJoint)) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "bridge-owned revolute joint", joint.getClass().getName(),
                    "Do not replace bridge-owned native joint identity.");
        }
        return revoluteJoint;
    }

    private void destroyConnectedJoints(EntityId entityId) {
        List<Box2dJointId> connected = joints.values().stream()
                .filter(joint -> joint.first().equals(entityId)
                        || joint.second().equals(entityId))
                .map(OwnedRevoluteJoint::id)
                .toList();
        connected.forEach(id -> destroyJoint(joints.remove(id)));
    }

    private void destroyAllJoints() {
        ArrayList<OwnedRevoluteJoint> remaining = new ArrayList<>(joints.values());
        Collections.reverse(remaining);
        remaining.forEach(this::destroyJoint);
        joints.clear();
    }

    private void destroyJoint(OwnedRevoluteJoint owned) {
        owned.registration().close();
        world.destroyJoint(owned.joint());
    }

    private void requireWorldUnlocked(String operation) {
        if (world.isLocked()) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "unlocked application-owned Box2D world", operation,
                    "Schedule native mutation outside World.step callbacks.");
        }
    }

    private static float finiteFloat(double value, String field) {
        float narrowed = (float) value;
        if (!Double.isFinite(value) || !Float.isFinite(narrowed)) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "finite float-representable " + field, Double.toString(value),
                    "Supply a finite value representable by the Box2D binding.");
        }
        return narrowed;
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

    private Box2dBodyHandle requireHandle(EntityId entityId) {
        EntityId checked = Objects.requireNonNull(entityId, "entityId");
        Box2dBodyHandle handle = bodies.get(checked);
        if (handle == null) {
            throw failure(GameplayDiagnosticCode.BOX2D_BODY_NOT_FOUND,
                    "active mapped body", checked.value(),
                    "Resolve copied body state before requesting a native transition.");
        }
        return handle;
    }

    private Box2dBodyHandle requireActiveDynamicBody(
            EntityId entityId, String operation) {
        Box2dBodyHandle handle = requireHandle(entityId);
        if (handle.bodyType() != BodyDef.BodyType.DynamicBody || !handle.body().isActive()) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "active dynamic mapped body",
                    entityId.value() + ":" + operation,
                    "Apply force or torque only to an active dynamic mapped body.");
        }
        return handle;
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

    private record OwnedRevoluteJoint(
            Box2dJointId id,
            EntityId first,
            EntityId second,
            Joint joint,
            Box2dRegistration<Joint> registration) {
        private OwnedRevoluteJoint {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(joint, "joint");
            Objects.requireNonNull(registration, "registration");
        }
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
