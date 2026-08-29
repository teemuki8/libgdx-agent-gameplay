package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.structs.b2JointId;
import com.badlogic.gdx.box2d.structs.b2Filter;
import com.badlogic.gdx.box2d.structs.b2Rot;
import com.badlogic.gdx.box2d.structs.b2QueryFilter;
import com.badlogic.gdx.box2d.structs.b2RevoluteJointDef;
import com.badlogic.gdx.box2d.structs.b2TreeStats;
import com.badlogic.gdx.box2d.structs.b2Vec2;
import com.badlogic.gdx.jnigen.runtime.closure.ClosureObject;
import com.badlogic.gdx.jnigen.runtime.pointer.VoidPointer;
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
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dInspection;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dRegistration;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dShapeSpec;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dUnitTransform;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owner-thread bridge from gameplay entities to private Box2D 3 IDs and copied evidence. */
public final class GameplayBox2dBridge implements LifecycleParticipant, AutoCloseable {
    private static final String WORLD_ID = "gameplay";

    private final GameplayBox2dWorld world;
    private final Box2dBodyFactory bodyFactory;
    private final Box2dUnitConversion units;
    private final AgentRuntime runtime;
    private final Thread ownerThread;
    private final Box2dInspection inspection;
    private final Box2dRegistration<com.badlogic.gdx.box2d.structs.b2WorldId> worldRegistration;
    private final Box2dContacts runtimeContacts;
    private final Box2dContactCollector contacts;
    private final Map<EntityId, Box2dBodyHandle> bodies = new TreeMap<>();
    private final Map<Box2dBodyHandle.ShapeKey, Box2dContactCollector.Endpoint> endpoints =
            new TreeMap<>();
    private final Map<Box2dJointId, OwnedRevoluteJoint> joints = new TreeMap<>();
    private final List<GameSystem> systems;
    private final b2Vec2 vectorScratch = new b2Vec2();
    private final b2Filter collisionFilterScratch = new b2Filter();
    private final b2Vec2 pointScratch = new b2Vec2();
    private final b2Vec2 anchorScratch = new b2Vec2();
    private final b2Vec2 rayOriginScratch = new b2Vec2();
    private final b2Vec2 rayTranslationScratch = new b2Vec2();
    private final b2Vec2 localAnchorA = new b2Vec2();
    private final b2Vec2 localAnchorB = new b2Vec2();
    private final b2Rot jointRotationA = new b2Rot();
    private final b2Rot jointRotationB = new b2Rot();
    private final b2Rot activationRotation = new b2Rot();
    private final b2RevoluteJointDef revoluteDef = new b2RevoluteJointDef();
    private final b2QueryFilter rayFilter = new b2QueryFilter();
    private final b2TreeStats rayStats = new b2TreeStats();
    private final VoidPointer nullContext = new VoidPointer(0L, false);
    private List<GameplayEvent> pendingContacts = List.of();
    private boolean closed;

    /**
     * Registers an opaque world and claims the owner-thread body factory for this bridge alone.
     */
    public GameplayBox2dBridge(GameplayBox2dWorld world, Box2dBodyFactory bodies,
            Box2dUnitConversion units, AgentRuntime runtime, GameplayLimits limits) {
        this.world = Objects.requireNonNull(world, "world");
        world.requireOwnerOpen();
        bodyFactory = Objects.requireNonNull(bodies, "bodies");
        this.units = Objects.requireNonNull(units, "units");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        GameplayLimits checkedLimits = Objects.requireNonNull(limits, "limits");
        if (!bodyFactory.units().equals(units)) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "matching bridge and body-factory units", bodyFactory.units().toString());
        }
        ownerThread = Thread.currentThread();
        bodyFactory.claimForBridge();
        inspection = new Box2dInspection(runtime, Box2dAdapterLimits.developmentDefaults());
        worldRegistration = inspection.registerWorld(WORLD_ID, world.id(),
                new io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dWorldSpec(
                        world.spec().subStepCount(),
                        new Box2dUnitTransform(units.renderUnitsPerMeter())));
        runtimeContacts = inspection.registerContacts(WORLD_ID,
                Box2dContactLimits.developmentDefaults(),
                Box2dContactPolicy.developmentDefaults());
        contacts = new Box2dContactCollector(checkedLimits.maxEventsPerTick());
        systems = List.of(new PrePhysicsSystem(), new Box2dPhysicsSystem(this),
                new PostPhysicsSystem());
    }

    /** Returns fixed PRE_PHYSICS, PHYSICS, and POST_PHYSICS systems. */
    public List<GameSystem> systems() {
        requireOwnerOpen("read-box2d-systems");
        return systems;
    }

    /** Returns copied stable body state without native identity. */
    public Optional<Box2dBodyState> bodyState(EntityId entityId) {
        requireOwnerOpen("read-box2d-body-state");
        Box2dBodyHandle handle = bodies.get(Objects.requireNonNull(entityId, "entityId"));
        return handle == null ? Optional.empty() : Optional.of(handle.state(units));
    }

    /**
     * Applies copied pose and velocity while enabling and waking one disabled mapped dynamic body.
     *
     * <p>Call this only on the bridge owner thread while the world is unlocked. To replace a
     * bridge-owned attachment, first deactivate the body and remove its previous joint, then
     * activate it and create the replacement joint. Native body identity never enters this API.
     *
     * @param activation stable entity plus copied render-unit pose and velocity
     */
    public void activate(Box2dBodyActivation activation) {
        requireMutable("activate-disabled-body");
        Box2dBodyActivation checked = Objects.requireNonNull(activation, "activation");
        Box2dBodyHandle handle = requireHandle(checked.entityId());
        if (handle.bodyType() != Box2dBodyType.DYNAMIC
                || Box2d.b2Body_IsEnabled(handle.body())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "disabled dynamic body for activation", checked.entityId().value());
        }
        copyVector(checked.positionRenderUnits(), pointScratch, true);
        copyVector(checked.velocityRenderUnitsPerSecond(), vectorScratch, true);
        Box2d.b2MakeRot(finiteFloat(checked.angleRadians(), "activation.angle"),
                activationRotation);
        float angularVelocity = finiteFloat(
                checked.angularVelocityRadiansPerSecond(), "activation.angularVelocity");
        Box2d.b2Body_SetTransform(handle.body(), pointScratch, activationRotation);
        Box2d.b2Body_Enable(handle.body());
        Box2d.b2Body_SetLinearVelocity(handle.body(), vectorScratch);
        Box2d.b2Body_SetAngularVelocity(handle.body(), angularVelocity);
        Box2d.b2Body_SetAwake(handle.body(), true);
    }

    /**
     * Creates one bridge-owned revolute joint from stable endpoints and a copied world anchor.
     *
     * <p>Call this after activating an endpoint when replacing an attachment. The bridge resolves
     * both private native bodies internally.
     *
     * @param spec stable joint ID, endpoint IDs, copied anchor, limits, and collision policy
     */
    public void createRevoluteJoint(Box2dRevoluteJointSpec spec) {
        requireMutable("create-revolute-joint");
        Box2dRevoluteJointSpec checked = Objects.requireNonNull(spec, "spec");
        if (joints.containsKey(checked.id())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "unique bridge-owned joint ID", checked.id().value());
        }
        if (joints.size() >= inspection.limits().joints()) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "bounded inspected joint count", Integer.toString(joints.size() + 1));
        }
        Box2dBodyHandle first = requireJointEndpoint(checked.first());
        Box2dBodyHandle second = requireJointEndpoint(checked.second());
        if (first == second) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "distinct native joint endpoints", checked.first().value());
        }
        Box2d.b2DefaultRevoluteJointDef(revoluteDef);
        revoluteDef.setBodyIdA(first.body());
        revoluteDef.setBodyIdB(second.body());
        anchorScratch.x(units.toPhysicsFloat(checked.anchorRenderUnits().x(), "joint.anchor.x"));
        anchorScratch.y(units.toPhysicsFloat(checked.anchorRenderUnits().y(), "joint.anchor.y"));
        Box2d.b2Body_GetLocalPoint(first.body(), anchorScratch, localAnchorA);
        Box2d.b2Body_GetLocalPoint(second.body(), anchorScratch, localAnchorB);
        revoluteDef.setLocalAnchorA(localAnchorA);
        revoluteDef.setLocalAnchorB(localAnchorB);
        revoluteDef.enableLimit(true);
        revoluteDef.lowerAngle(finiteFloat(checked.lowerAngleRadians(), "joint.lowerAngle"));
        Box2d.b2Body_GetRotation(first.body(), jointRotationA);
        Box2d.b2Body_GetRotation(second.body(), jointRotationB);
        revoluteDef.referenceAngle(finiteFloat(
                Box2d.b2Rot_GetAngle(jointRotationB) - Box2d.b2Rot_GetAngle(jointRotationA),
                "joint.referenceAngle"));
        revoluteDef.upperAngle(finiteFloat(checked.upperAngleRadians(), "joint.upperAngle"));
        revoluteDef.collideConnected(checked.collideConnected());
        b2JointId joint = Box2d.b2CreateRevoluteJoint(world.id(), revoluteDef.asPointer());
        Box2dRegistration<b2JointId> registration = null;
        try {
            registration = inspection.registerJoint(checked.id().value(), WORLD_ID, joint);
            joints.put(checked.id(), new OwnedRevoluteJoint(checked.id(), checked.first(),
                    checked.second(), joint, registration));
        } catch (RuntimeException | Error failure) {
            if (registration != null) registration.close();
            if (Box2d.b2Joint_IsValid(joint)) Box2d.b2DestroyJoint(joint);
            throw failure;
        }
    }

    /** Configures one bridge-owned revolute motor. */
    public void configureRevoluteMotor(Box2dJointId id, Box2dRevoluteMotor motor) {
        requireMutable("configure-revolute-motor");
        b2JointId joint = requireRevoluteJoint(id).joint();
        Box2dRevoluteMotor checked = Objects.requireNonNull(motor, "motor");
        Box2d.b2RevoluteJoint_SetMotorSpeed(joint,
                finiteFloat(checked.speedRadiansPerSecond(), "motor.speed"));
        Box2d.b2RevoluteJoint_SetMaxMotorTorque(joint,
                finiteFloat(checked.maximumTorqueNewtonMetres(), "motor.torque"));
        Box2d.b2RevoluteJoint_EnableMotor(joint, checked.enabled());
    }

    /** Applies SI force at the body's centre and wakes an active dynamic body. */
    public void applyForceToCenter(EntityId entityId, Vec2 forceNewtons) {
        requireMutable("apply-force-to-center");
        Box2dBodyHandle handle = requireActiveDynamic(entityId, "apply-force-to-center");
        copyVector(Objects.requireNonNull(forceNewtons, "forceNewtons"), vectorScratch, false);
        Box2d.b2Body_ApplyForceToCenter(handle.body(), vectorScratch, true);
    }

    /**
     * Replaces the collision filter on one active bridge-mapped shape.
     *
     * <p>The bridge resolves its private native shape identity internally and copies every filter
     * value into Box2D. Call this only on the bridge owner thread while the application-owned world
     * is unlocked.
     *
     * @param entityId stable gameplay entity whose single mapped shape will be updated
     * @param filter copied unsigned category/mask bits and signed collision group
     */
    public void configureCollisionFilter(EntityId entityId, Box2dCollisionFilter filter) {
        requireMutable("configure-collision-filter");
        Box2dBodyHandle handle = requireHandle(entityId);
        if (!Box2d.b2Body_IsEnabled(handle.body())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "active mapped body for configure-collision-filter", entityId.value());
        }
        Box2dCollisionFilter checked = Objects.requireNonNull(filter, "filter");
        collisionFilterScratch.categoryBits(checked.categoryBits());
        collisionFilterScratch.maskBits(checked.maskBits());
        collisionFilterScratch.groupIndex(checked.groupIndex());
        Box2d.b2Shape_SetFilter(handle.shape(), collisionFilterScratch);
    }

    /** Applies SI torque and wakes an active dynamic body. */
    public void applyTorque(EntityId entityId, double torqueNewtonMetres) {
        requireMutable("apply-torque");
        Box2d.b2Body_ApplyTorque(requireActiveDynamic(entityId, "apply-torque").body(),
                finiteFloat(torqueNewtonMetres, "torque"), true);
    }

    /** Applies SI force at a copied world point expressed in render units. */
    public void applyForce(EntityId entityId, Vec2 forceNewtons,
            Vec2 worldPointRenderUnits) {
        requireMutable("apply-force-at-point");
        Box2dBodyHandle handle = requireActiveDynamic(entityId, "apply-force-at-point");
        copyVector(Objects.requireNonNull(forceNewtons, "forceNewtons"), vectorScratch, false);
        copyVector(Objects.requireNonNull(worldPointRenderUnits, "worldPointRenderUnits"),
                pointScratch, true);
        Box2d.b2Body_ApplyForce(handle.body(), vectorScratch, pointScratch, true);
    }

    /** Returns copied revolute state, or empty for an unknown stable ID. */
    public Optional<Box2dRevoluteJointState> revoluteJointState(Box2dJointId id) {
        requireOwnerOpen("read-revolute-state");
        OwnedRevoluteJoint owned = joints.get(Objects.requireNonNull(id, "id"));
        if (owned == null) return Optional.empty();
        requireLive(owned.joint());
        double speed = Box2d.b2Body_GetAngularVelocity(requireHandle(owned.second()).body())
                - Box2d.b2Body_GetAngularVelocity(requireHandle(owned.first()).body());
        return Optional.of(new Box2dRevoluteJointState(owned.id(), owned.first(), owned.second(),
                Box2d.b2RevoluteJoint_GetAngle(owned.joint()), speed,
                Box2d.b2RevoluteJoint_IsMotorEnabled(owned.joint()),
                Box2d.b2RevoluteJoint_GetMotorSpeed(owned.joint()),
                Box2d.b2RevoluteJoint_GetMaxMotorTorque(owned.joint())));
    }

    /**
     * Removes a bridge-owned joint; an absent stable ID is already removed.
     *
     * <p>Removing a joint never exposes or invalidates either endpoint's private native body.
     *
     * @param id stable bridge-owned joint ID
     */
    public void removeJoint(Box2dJointId id) {
        requireMutable("remove-joint");
        OwnedRevoluteJoint owned = joints.remove(Objects.requireNonNull(id, "id"));
        if (owned != null) destroyJoint(owned);
    }

    /** Returns a sorted immutable bounded raycast result. */
    public List<Box2dRaycastHit> raycast(Box2dRaycastSpec spec) {
        requireOwnerOpen("raycast-box2d-world");
        Box2dRaycastSpec checked = Objects.requireNonNull(spec, "spec");
        copyVector(checked.originRenderUnits(), rayOriginScratch, true);
        copyVector(checked.translationRenderUnits(), rayTranslationScratch, true);
        Box2d.b2DefaultQueryFilter(rayFilter);
        rayFilter.categoryBits(checked.categoryBits());
        rayFilter.maskBits(checked.maskBits());
        ArrayList<Box2dRaycastHit> hits = new ArrayList<>(checked.maxHits());
        ClosureObject<Box2d.b2CastResultFcn> closure = ClosureObject.fromClosure(
                (shapeId, point, normal, fraction, context) -> {
                    Box2dContactCollector.Endpoint endpoint =
                            endpoints.get(Box2dBodyHandle.ShapeKey.copyOf(shapeId));
                    if (endpoint == null) return 1.0f;
                    int replacement = hits.size();
                    if (hits.size() >= checked.maxHits()) {
                        replacement = worstRaycastHit(hits);
                        Box2dRaycastHit worst = hits.get(replacement);
                        if (fraction > worst.fraction()
                                || fraction == worst.fraction()
                                && endpoint.fixtureId().compareTo(worst.fixtureId()) >= 0) {
                            return 1.0f;
                        }
                    }
                    Box2dRaycastHit copied = new Box2dRaycastHit(
                            endpoint.entityId(), endpoint.fixtureId(),
                            units.toRenderUnits(point.x(), point.y()),
                            new Vec2(normal.x(), normal.y()), fraction);
                    if (replacement == hits.size()) {
                        hits.add(copied);
                    } else {
                        hits.set(replacement, copied);
                    }
                    return 1.0f;
                });
        try {
            Box2d.b2World_CastRay(world.id(), rayOriginScratch, rayTranslationScratch,
                    rayFilter, closure, nullContext, rayStats);
        } finally {
            closure.free();
        }
        hits.sort(Comparator.comparingDouble(Box2dRaycastHit::fraction)
                .thenComparing(Box2dRaycastHit::fixtureId));
        return List.copyOf(hits);
    }

    /**
     * Disables one mapped body while retaining its private native mapping.
     *
     * <p>This operation does not remove connected bridge-owned joints. Remove the old joint
     * explicitly before reactivating the body at a copied pose and creating a replacement joint.
     *
     * @param entityId stable gameplay entity whose mapped body will be disabled
     */
    public void deactivate(EntityId entityId) {
        requireMutable("deactivate-body");
        Box2dBodyHandle handle = requireHandle(entityId);
        if (Box2d.b2Body_IsEnabled(handle.body())) Box2d.b2Body_Disable(handle.body());
    }

    /** Stops one mapped body without exposing native identity. */
    public void stop(EntityId entityId) {
        requireMutable("stop-body");
        vectorScratch.x(0.0f);
        vectorScratch.y(0.0f);
        Box2d.b2Body_SetLinearVelocity(requireHandle(entityId).body(), vectorScratch);
    }

    Optional<Box2dBodyHandle> handle(EntityId entityId) {
        requireOwnerOpen("read-internal-handle");
        return Optional.ofNullable(bodies.get(Objects.requireNonNull(entityId, "entityId")));
    }

    /** Returns immutable render/SI conversion. */
    public Box2dUnitConversion units() { return units; }

    @Override public int dependencyLevel() { return 100; }

    @Override public void onActivate(EntityView entity) {
        requireMutable("activate-body");
        if (entity.component(Transform2D.TYPE).isEmpty()
                || entity.component(Collider.TYPE).isEmpty()) return;
        if (bodies.size() >= inspection.limits().bodies()) {
            throw failure(GameplayDiagnosticCode.BOX2D_BODY_LIMIT_EXCEEDED,
                    "bounded inspected body count", Integer.toString(bodies.size() + 1));
        }
        if (bodies.containsKey(entity.id())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "one body per active entity", entity.id().value());
        }
        Box2dBodyHandle handle = bodyFactory.create(world, entity);
        Box2dRegistration<com.badlogic.gdx.box2d.structs.b2BodyId> bodyRegistration = null;
        try {
            bodyRegistration = inspection.registerBody(entity.id().value(), WORLD_ID, handle.body());
            Box2dRegistration<com.badlogic.gdx.box2d.structs.b2ShapeId> shapeRegistration =
                    inspection.registerShape(handle.fixtureId(), entity.id().value(),
                            handle.shape(), Box2dShapeSpec.defaults());
            handle.attachInspection(bodyRegistration, shapeRegistration);
            bodies.put(entity.id(), handle);
            endpoints.put(Box2dBodyHandle.ShapeKey.copyOf(handle.shape()),
                    new Box2dContactCollector.Endpoint(entity.id(), handle.fixtureId()));
        } catch (RuntimeException | Error failure) {
            if (bodyRegistration != null) bodyRegistration.close();
            Box2dNativeDisposal.destroy(world, handle);
            throw failure;
        }
    }

    @Override public void onLogicalDespawn(EntityView entity) {
        requireMutable("logical-despawn-body");
        Box2dBodyHandle handle = bodies.get(entity.id());
        if (handle != null && Box2d.b2Body_IsEnabled(handle.body())) {
            Box2d.b2Body_Disable(handle.body());
        }
    }

    @Override public void onDispose(EntityId entityId) {
        requireMutable("dispose-body");
        EntityId checked = Objects.requireNonNull(entityId, "entityId");
        destroyConnectedJoints(checked);
        Box2dBodyHandle handle = bodies.remove(checked);
        if (handle != null) {
            endpoints.remove(Box2dBodyHandle.ShapeKey.copyOf(handle.shape()));
            Box2dNativeDisposal.destroy(world, handle);
        }
    }

    @Override public void onReset() {
        requireMutable("reset-bridge");
        destroyAllJoints();
        contacts.reset();
        pendingContacts = List.of();
    }

    @Override public void onClose() { close(); }

    /** Destroys only bridge-owned joints, shapes, bodies, registrations, and scratch. */
    @Override public void close() {
        requireOwner("close-bridge");
        if (closed) return;
        world.requireUnlocked();
        runtimeContacts.close();
        destroyAllJoints();
        ArrayList<Box2dBodyHandle> remaining = new ArrayList<>(bodies.values());
        Collections.reverse(remaining);
        remaining.forEach(handle -> Box2dNativeDisposal.destroy(world, handle));
        bodies.clear();
        endpoints.clear();
        pendingContacts = List.of();
        contacts.close();
        worldRegistration.close();
        inspection.close();
        closed = true;
    }

    void stepPhysics(SystemContext context) {
        requireOwnerOpen("step-world");
        float seconds = seconds(context.fixedStepNanos());
        pendingContacts = contacts.captureStep(world.id(), endpoints, () -> {
            Runnable step = () -> world.step(seconds);
            if (runtime.simulation().activeTick().isPresent()) {
                runtimeContacts.captureStep(step);
            } else {
                step.run();
            }
        });
    }

    private void applyCommands(SystemContext context) {
        Map<EntityId, EntityView> moving = context.query(Movement.TYPE).stream()
                .collect(Collectors.toUnmodifiableMap(EntityView::id, Function.identity()));
        for (var envelope : context.commands()) {
            if (!(envelope.command() instanceof MoveCommand move)) continue;
            Box2dBodyHandle handle = bodies.get(move.entityId());
            if (handle == null || handle.bodyType() == Box2dBodyType.STATIC
                    || !Box2d.b2Body_IsEnabled(handle.body())) continue;
            EntityView entity = moving.get(move.entityId());
            if (entity == null) continue;
            double x = move.direction().x();
            double y = move.direction().y();
            double length = Math.hypot(x, y);
            if (length > 1.0) {
                x /= length;
                y /= length;
            }
            double speed = entity.component(Movement.TYPE).orElseThrow().maxSpeed();
            vectorScratch.x(units.toPhysicsFloat(x * speed, "command.velocity.x"));
            vectorScratch.y(units.toPhysicsFloat(y * speed, "command.velocity.y"));
            Box2d.b2Body_SetLinearVelocity(handle.body(), vectorScratch);
        }
    }

    private void synchronizeAuthority(SystemContext context) {
        for (EntityView entity : context.query(Transform2D.TYPE, Collider.TYPE)) {
            Box2dBodyHandle handle = bodies.get(entity.id());
            if (handle == null || handle.bodyType() == Box2dBodyType.STATIC
                    || !Box2d.b2Body_IsEnabled(handle.body())) continue;
            Box2dBodyState state = handle.state(units);
            Transform2D previous = entity.component(Transform2D.TYPE).orElseThrow();
            context.replace(entity.id(), Transform2D.TYPE, new Transform2D(
                    state.positionRenderUnits(), state.angleRadians(), previous.size(),
                    previous.pivot()));
            entity.component(Movement.TYPE).ifPresent(previousMovement ->
                    context.replace(entity.id(), Movement.TYPE,
                            new Movement(state.velocityRenderUnitsPerSecond(),
                                    previousMovement.maxSpeed())));
        }
        pendingContacts.forEach(context::emit);
        pendingContacts = List.of();
    }

    private Box2dBodyHandle requireJointEndpoint(EntityId entityId) {
        Box2dBodyHandle handle = requireHandle(entityId);
        if (handle.bodyType() == Box2dBodyType.STATIC) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "non-static joint endpoint", entityId.value());
        }
        return handle;
    }

    private Box2dBodyHandle requireActiveDynamic(EntityId entityId, String operation) {
        Box2dBodyHandle handle = requireHandle(entityId);
        if (handle.bodyType() != Box2dBodyType.DYNAMIC
                || !Box2d.b2Body_IsEnabled(handle.body())) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "active dynamic body for " + operation, entityId.value());
        }
        return handle;
    }

    private Box2dBodyHandle requireHandle(EntityId entityId) {
        EntityId checked = Objects.requireNonNull(entityId, "entityId");
        Box2dBodyHandle handle = bodies.get(checked);
        if (handle == null || !Box2d.b2Body_IsValid(handle.body())) {
            throw failure(GameplayDiagnosticCode.BOX2D_BODY_NOT_FOUND,
                    "live mapped body", checked.value());
        }
        return handle;
    }

    private OwnedRevoluteJoint requireRevoluteJoint(Box2dJointId id) {
        OwnedRevoluteJoint owned = joints.get(Objects.requireNonNull(id, "id"));
        if (owned == null) {
            throw failure(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "bridge-owned revolute joint", id.value());
        }
        requireLive(owned.joint());
        return owned;
    }

    private static void requireLive(b2JointId id) {
        if (!Box2d.b2Joint_IsValid(id)) throw new IllegalStateException("stale joint ID");
    }

    private void destroyConnectedJoints(EntityId entityId) {
        List<Box2dJointId> connected = joints.values().stream()
                .filter(joint -> joint.first().equals(entityId) || joint.second().equals(entityId))
                .map(OwnedRevoluteJoint::id).toList();
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
        if (Box2d.b2Joint_IsValid(owned.joint())) Box2d.b2DestroyJoint(owned.joint());
    }

    private void copyVector(Vec2 value, b2Vec2 target, boolean renderUnits) {
        target.x(renderUnits ? units.toPhysicsFloat(value.x(), "vector.x")
                : finiteFloat(value.x(), "vector.x"));
        target.y(renderUnits ? units.toPhysicsFloat(value.y(), "vector.y")
                : finiteFloat(value.y(), "vector.y"));
    }

    private static int worstRaycastHit(List<Box2dRaycastHit> hits) {
        int worst = 0;
        for (int index = 1; index < hits.size(); index++) {
            Box2dRaycastHit candidate = hits.get(index);
            Box2dRaycastHit current = hits.get(worst);
            if (candidate.fraction() > current.fraction()
                    || candidate.fraction() == current.fraction()
                    && candidate.fixtureId().compareTo(current.fixtureId()) > 0) {
                worst = index;
            }
        }
        return worst;
    }

    private void requireMutable(String operation) {
        requireOwnerOpen(operation);
        world.requireUnlocked();
    }

    private void requireOwnerOpen(String operation) {
        requireOwner(operation);
        if (closed) throw failure(GameplayDiagnosticCode.BOX2D_BRIDGE_CLOSED,
                "open bridge", "closed");
        world.requireOwnerOpen();
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw GameplayException.validation(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    operation, "bridge owner thread", Thread.currentThread().getName(),
                    "Run native operations on the bridge owner thread.");
        }
    }

    private static float finiteFloat(double value, String field) {
        return Box2dBodyFactory.finiteFloat(value, field);
    }

    private static float seconds(long nanos) {
        float value = nanos / 1_000_000_000.0f;
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException("fixed step must be positive and float-representable");
        }
        return value;
    }

    private static GameplayException failure(GameplayDiagnosticCode code,
            String expected, String observed) {
        return GameplayException.validation(code, "operate-box2d3-bridge", expected, observed,
                "Use live bridge-owned copied identities on the owner thread.");
    }

    private record OwnedRevoluteJoint(Box2dJointId id, EntityId first, EntityId second,
            b2JointId joint, Box2dRegistration<b2JointId> registration) {
    }

    private final class PrePhysicsSystem implements GameSystem {
        private final SystemDescriptor descriptor = new SystemDescriptor(
                SystemId.of("box2d-apply-intent"), SystemPhase.PRE_PHYSICS, 10);
        @Override public SystemDescriptor descriptor() { return descriptor; }
        @Override public void update(SystemContext context) { applyCommands(context); }
    }

    private final class PostPhysicsSystem implements GameSystem {
        private final SystemDescriptor descriptor = new SystemDescriptor(
                SystemId.of("box2d-copy-authority"), SystemPhase.POST_PHYSICS, 10);
        @Override public SystemDescriptor descriptor() { return descriptor; }
        @Override public void update(SystemContext context) { synchronizeAuthority(context); }
    }
}
