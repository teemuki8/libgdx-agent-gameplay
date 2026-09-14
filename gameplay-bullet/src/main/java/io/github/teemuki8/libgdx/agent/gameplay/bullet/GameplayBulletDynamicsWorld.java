package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btGeneric6DofConstraint;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/** Application-owned, owner-thread-confined Bullet rigid-body world with private native identities. */
public final class GameplayBulletDynamicsWorld implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final BulletDynamicsLimits limits;
    private final Map<EntityId, Body> bodies = new TreeMap<>();
    private final Map<BulletConstraintId, Constraint> constraints = new TreeMap<>();
    private final Set<BodyPair> ignoredCollisions = new HashSet<>();
    private final btDefaultCollisionConfiguration configuration;
    private final btCollisionDispatcher dispatcher;
    private final btBroadphaseInterface broadphase;
    private final btConstraintSolver solver;
    private final btDiscreteDynamicsWorld world;
    private boolean closed;
    private long steps;

    /** Allocates one bounded native dynamics world with an explicit gravity vector in metres per second squared. */
    public GameplayBulletDynamicsWorld(BulletDynamicsLimits limits, Vec3 gravity) {
        this.limits = Objects.requireNonNull(limits, "limits");
        Vector3 nativeGravity = vector(Objects.requireNonNull(gravity, "gravity"));
        Bullet.init();
        btDefaultCollisionConfiguration acquiredConfiguration = null;
        btCollisionDispatcher acquiredDispatcher = null;
        btBroadphaseInterface acquiredBroadphase = null;
        btConstraintSolver acquiredSolver = null;
        btDiscreteDynamicsWorld acquiredWorld = null;
        try {
            acquiredConfiguration = new btDefaultCollisionConfiguration();
            acquiredDispatcher = new btCollisionDispatcher(acquiredConfiguration);
            acquiredBroadphase = new btDbvtBroadphase();
            acquiredSolver = new btSequentialImpulseConstraintSolver();
            acquiredWorld = new btDiscreteDynamicsWorld(acquiredDispatcher, acquiredBroadphase,
                    acquiredSolver, acquiredConfiguration);
            acquiredWorld.setGravity(nativeGravity);
        } catch (RuntimeException | Error failure) {
            dispose(acquiredWorld);
            dispose(acquiredSolver);
            dispose(acquiredBroadphase);
            dispose(acquiredDispatcher);
            dispose(acquiredConfiguration);
            throw failure;
        }
        configuration = acquiredConfiguration;
        dispatcher = acquiredDispatcher;
        broadphase = acquiredBroadphase;
        solver = acquiredSolver;
        world = acquiredWorld;
    }

    /** Creates one private native body under its stable gameplay entity ID. */
    public void add(EntityId id, BulletRigidBodySpec spec) {
        add(id, spec, Vec3.ZERO, Vec3.ZERO);
    }

    /** Creates one body with copied initial linear and angular velocities for state transfer. */
    public void add(EntityId id, BulletRigidBodySpec spec, Vec3 linearVelocity, Vec3 angularVelocity) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        Matrix4 initialPose = matrix(spec.pose());
        Vector3 initialLinearVelocity = vector(linearVelocity);
        Vector3 initialAngularVelocity = vector(angularVelocity);
        if (bodies.size() >= limits.maxBodies() || bodies.containsKey(id)) {
            throw new IllegalArgumentException("body limit or duplicate entity: " + id);
        }
        btCollisionShape shape = shape(spec.shape());
        btDefaultMotionState motion = null;
        btRigidBody body = null;
        try {
            motion = new btDefaultMotionState(initialPose);
            Vector3 inertia = new Vector3();
            if (spec.massKilograms() > 0) {
                shape.calculateLocalInertia((float) spec.massKilograms(), inertia);
            }
            body = new btRigidBody((float) spec.massKilograms(), motion, shape, inertia);
            body.setFriction((float) spec.friction());
            body.setRestitution((float) spec.restitution());
            body.setDamping((float) spec.linearDamping(), (float) spec.angularDamping());
            body.setLinearVelocity(initialLinearVelocity);
            body.setAngularVelocity(initialAngularVelocity);
            world.addRigidBody(body, spec.collisionGroup(), spec.collisionMask());
            bodies.put(id, new Body(body, motion, shape, spec.massKilograms()));
        } catch (RuntimeException | Error failure) {
            if (body != null) {
                body.dispose();
            }
            dispose(motion);
            shape.dispose();
            throw failure;
        }
    }

    /** Locks anchor translation and applies bounded XYZ angular limits between active endpoint bodies. */
    public void constrain(BulletConstraintId id, BulletSixDofConstraintSpec spec) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        if (constraints.size() >= limits.maxConstraints() || constraints.containsKey(id)) {
            throw new IllegalArgumentException("constraint limit or duplicate ID: " + id);
        }
        Body first = requireBody(spec.first()), second = requireBody(spec.second());
        Matrix4 anchor = matrix(new Transform3D(spec.anchorWorld(), spec.anchorRotationWorld()));
        Matrix4 frameA = new Matrix4(first.body().getWorldTransform()).inv().mul(anchor);
        Matrix4 frameB = new Matrix4(second.body().getWorldTransform()).inv().mul(anchor);
        var constraint = new btGeneric6DofConstraint(first.body(), second.body(), frameA, frameB, true);
        try {
            constraint.setLinearLowerLimit(Vector3.Zero);
            constraint.setLinearUpperLimit(Vector3.Zero);
            constraint.setAngularLowerLimit(vector(spec.angularLowerRadians()));
            constraint.setAngularUpperLimit(vector(spec.angularUpperRadians()));
            world.addConstraint(constraint, spec.disableLinkedCollision());
            constraints.put(id, new Constraint(constraint, spec.first(), spec.second()));
            if (spec.disableLinkedCollision()) clearCachedContacts(first.mass() > 0 ? first : second);
        } catch (RuntimeException | Error failure) {
            constraint.dispose();
            throw failure;
        }
    }

    /** Applies one finite world-space impulse to a dynamic body. */
    public void applyCentralImpulse(EntityId id, Vec3 impulseNewtonSeconds) {
        requireOpen();
        Body body = requireDynamicBody(id, "impulse");
        body.body().activate();
        body.body().applyCentralImpulse(vector(Objects.requireNonNull(impulseNewtonSeconds, "impulseNewtonSeconds")));
    }

    /** Applies a finite impulse at a world point, allowing bounded impact torque. */
    public void applyImpulse(EntityId id, Vec3 impulseNewtonSeconds, Vec3 worldPoint) {
        requireOpen();
        Body body = requireDynamicBody(id, "impulse");
        Vector3 point = vector(Objects.requireNonNull(worldPoint, "worldPoint"));
        Vector3 centre = new Vector3();
        body.body().getWorldTransform().getTranslation(centre);
        body.body().activate();
        body.body().applyImpulse(vector(Objects.requireNonNull(impulseNewtonSeconds, "impulseNewtonSeconds")), point.sub(centre));
    }

    /** Applies a finite world-space force for the caller's next fixed physics step. */
    public void applyCentralForce(EntityId id, Vec3 forceNewtons) {
        requireOpen();
        Body body = requireDynamicBody(id, "force");
        body.body().activate();
        body.body().applyCentralForce(vector(Objects.requireNonNull(forceNewtons, "forceNewtons")));
    }

    /** Applies a finite world-space torque for the caller's next fixed physics step. */
    public void applyTorque(EntityId id, Vec3 torqueNewtonMetres) {
        requireOpen();
        Body body = requireDynamicBody(id, "torque");
        body.body().activate();
        body.body().applyTorque(vector(Objects.requireNonNull(torqueNewtonMetres, "torqueNewtonMetres")));
    }

    /** Changes one bounded body-pair collision exception without exposing either native body. */
    public void setCollisionIgnored(EntityId firstId, EntityId secondId, boolean ignored) {
        requireOpen();
        Body first = requireBody(firstId);
        Body second = requireBody(secondId);
        if (firstId.equals(secondId)) {
            throw new IllegalArgumentException("collision pair requires two distinct bodies");
        }
        BodyPair pair = BodyPair.of(firstId, secondId);
        if (ignoredCollisions.contains(pair) == ignored) return;
        if (ignored && ignoredCollisions.size() >= limits.maxBodies() * 8L) {
            throw new IllegalArgumentException("ignored collision-pair limit reached");
        }
        first.body().setIgnoreCollisionCheck(second.body(), ignored);
        second.body().setIgnoreCollisionCheck(first.body(), ignored);
        if (ignored) ignoredCollisions.add(pair);
        else ignoredCollisions.remove(pair);
        clearCachedContacts(first.mass() > 0 ? first : second);
        first.body().activate();
        second.body().activate();
    }

    private void clearCachedContacts(Body body) {
        // Use Bullet's proxy callback to clear real cached pairs. gdx-bullet 1.14.2's
        // direct cleanOverlappingPair wrapper has an invalid native-reference typemap.
        world.getPairCache().cleanProxyFromPairs(body.body().getBroadphaseHandle(), dispatcher);
    }

    /** Advances exactly one caller-owned fixed step in seconds. */
    public void step(double fixedStepSeconds) {
        requireOpen();
        if (!Double.isFinite(fixedStepSeconds) || fixedStepSeconds < .001 || fixedStepSeconds > .1) {
            throw new IllegalArgumentException("fixed step must be .001..0.1 seconds");
        }
        world.stepSimulation((float) fixedStepSeconds, 1, (float) fixedStepSeconds);
        steps++;
    }

    /** Returns copied pose and velocity facts for one active body. */
    public Optional<BulletRigidBodyState> state(EntityId id) {
        requireOpen();
        Body body = bodies.get(Objects.requireNonNull(id, "id"));
        if (body == null) {
            return Optional.empty();
        }
        var transform = new Matrix4();
        body.body().getWorldTransform(transform);
        var position = new Vector3();
        transform.getTranslation(position);
        var rotation = new Quaternion();
        transform.getRotation(rotation, true);
        return Optional.of(new BulletRigidBodyState(copy(position),
                new QuaternionValue(rotation.x, rotation.y, rotation.z, rotation.w),
                copy(body.body().getLinearVelocity()), copy(body.body().getAngularVelocity())));
    }

    /** Removes a constraint if present. */
    public void removeConstraint(BulletConstraintId id) {
        requireOpen();
        Constraint constraint = constraints.remove(Objects.requireNonNull(id, "id"));
        if (constraint != null) {
            world.removeConstraint(constraint.nativeConstraint());
            constraint.nativeConstraint().dispose();
        }
    }

    /** Removes a body only when no registered constraint still names it. */
    public void remove(EntityId id) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        if (constraints.values().stream().anyMatch(value -> value.first().equals(id) || value.second().equals(id))) {
            throw new IllegalStateException("remove body constraints first: " + id);
        }
        Body body = bodies.remove(id);
        if (body != null) {
            ignoredCollisions.removeIf(pair -> {
                if (!pair.includes(id)) return false;
                EntityId otherId = pair.other(id);
                Body other = bodies.get(otherId);
                if (other != null) {
                    body.body().setIgnoreCollisionCheck(other.body(), false);
                    other.body().setIgnoreCollisionCheck(body.body(), false);
                }
                return true;
            });
            dispose(body);
        }
    }

    /** Reports bounded counts without exposing native identities. */
    public BulletDynamicsStatistics statistics() {
        requireOpen();
        return new BulletDynamicsStatistics(bodies.size(), constraints.size(), steps);
    }

    private Body requireBody(EntityId id) {
        Body body = bodies.get(Objects.requireNonNull(id, "id"));
        if (body == null) {
            throw new IllegalArgumentException("unknown body: " + id);
        }
        return body;
    }

    private Body requireDynamicBody(EntityId id, String operation) {
        Body body = requireBody(id);
        if (body.mass() <= 0) {
            throw new IllegalArgumentException("static body cannot receive " + operation + ": " + id);
        }
        return body;
    }

    private static btCollisionShape shape(BulletBodyShape definition) {
        Vector3 size = vector(definition.size());
        return switch (definition.kind()) {
            case BOX -> new btBoxShape(size.scl(.5f));
            case CAPSULE_Y -> new btCapsuleShape(size.x * .5f, size.y - size.x);
            case SPHERE -> new btSphereShape(size.x * .5f);
        };
    }
    private static Matrix4 matrix(Transform3D pose) {
        QuaternionValue q = pose.rotation();
        return new Matrix4(vector(pose.position()),
                new Quaternion((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()), Vector3.One);
    }

    private static Vector3 vector(Vec3 value) {
        if (Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z()))) > 1e6) {
            throw new IllegalArgumentException("Bullet vector exceeds one million metres");
        }
        return new Vector3((float) value.x(), (float) value.y(), (float) value.z());
    }

    private static Vec3 copy(Vector3 value) {
        return new Vec3(value.x, value.y, value.z);
    }

    private void requireOpen() {
        if (Thread.currentThread() != owner) throw GameplayException.validation(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                "access-bullet-dynamics", "construction thread", "different thread", "Use the owner thread.");
        if (closed) throw GameplayException.validation(GameplayDiagnosticCode.WORLD_CLOSED,
                "access-bullet-dynamics", "open world", "closed world", "Create a new world.");
    }
    private void dispose(Body body) {
        world.removeRigidBody(body.body());
        body.body().dispose();
        body.motion().dispose();
        body.shape().dispose();
    }

    private static void dispose(com.badlogic.gdx.utils.Disposable resource) {
        if (resource != null) {
            resource.dispose();
        }
    }

    /** Releases constraints before bodies and then the application-owned native world resources. */
    @Override public void close() {
        if (Thread.currentThread() != owner) throw GameplayException.validation(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                "access-bullet-dynamics", "construction thread", "different thread", "Use the owner thread.");
        if (closed) {
            return;
        }
        constraints.values().forEach(value -> {
            world.removeConstraint(value.nativeConstraint());
            value.nativeConstraint().dispose();
        });
        constraints.clear();
        ignoredCollisions.clear();
        bodies.values().forEach(this::dispose);
        bodies.clear();
        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        configuration.dispose();
        closed = true;
    }

    private record Body(btRigidBody body, btDefaultMotionState motion, btCollisionShape shape, double mass) { }

    private record Constraint(btGeneric6DofConstraint nativeConstraint, EntityId first, EntityId second) { }

    private record BodyPair(EntityId first, EntityId second) {
        private static BodyPair of(EntityId first, EntityId second) {
            return first.compareTo(second) < 0 ? new BodyPair(first, second) : new BodyPair(second, first);
        }
        private boolean includes(EntityId id) { return first.equals(id) || second.equals(id); }
        private EntityId other(EntityId id) { return first.equals(id) ? second : first; }
    }
}
