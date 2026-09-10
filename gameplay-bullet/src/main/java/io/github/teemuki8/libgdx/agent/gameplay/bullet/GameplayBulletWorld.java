package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.ClosestConvexResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ContactResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObjectWrapper;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btManifoldPoint;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Application-owned, owner-thread-confined static collision world. Units are metres, Y is up. */
public final class GameplayBulletWorld implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final int maxBodies;
    private final Map<EntityId, Body> bodies = new TreeMap<>();
    private final btDefaultCollisionConfiguration configuration;
    private final btCollisionDispatcher dispatcher;
    private final btDbvtBroadphase broadphase;
    private final btCollisionWorld world;
    private boolean closed;

    /** Allocates a bounded native world. The application closes this world after its bridge. */
    public GameplayBulletWorld(int maxBodies) {
        if (maxBodies < 1 || maxBodies > 4096) throw new IllegalArgumentException("maxBodies: 1..4096");
        this.maxBodies = maxBodies;
        Bullet.init();
        configuration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(configuration);
        broadphase = new btDbvtBroadphase();
        world = new btCollisionWorld(dispatcher, broadphase, configuration);
    }

    /** Adds a static box; rotation authors slopes. Dimensions include the transform's positive scale. */
    public void addBox(EntityId id, Transform3D pose, Vec3 halfExtents) {
        requireOpen();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pose, "pose");
        if (bodies.size() == maxBodies || bodies.containsKey(id)) {
            throw new IllegalArgumentException("body limit or duplicate entity: " + id);
        }
        Vector3 size = vector(halfExtents).scl(vector(pose.scale()));
        if (size.x <= 0 || size.y <= 0 || size.z <= 0 || Math.max(size.x, Math.max(size.y, size.z)) > 1e6) throw new IllegalArgumentException("positive extents");
        var shape = new btBoxShape(size);
        var body = new btCollisionObject();
        try {
            body.setCollisionShape(shape);
            body.setWorldTransform(matrix(pose));
            world.addCollisionObject(body);
            bodies.put(id, new Body(body, shape, pose.scale()));
        } catch (RuntimeException | Error error) {
            body.dispose();
            shape.dispose();
            throw error;
        }
    }

    /** Moves an existing box and refreshes its broadphase bounds. Scale changes require remove/add. */
    public void updateBoxTransform(EntityId id, Transform3D pose) {
        requireOpen();
        Objects.requireNonNull(pose, "pose");
        Body body = bodies.get(Objects.requireNonNull(id, "id"));
        if (body == null) throw new IllegalArgumentException("unknown body: " + id);
        if (!body.scale().equals(pose.scale())) throw new IllegalArgumentException("box scale changes require remove/add");
        body.object().setWorldTransform(matrix(pose));
        world.updateSingleAabb(body.object());
    }

    /** Removes only the adapter-owned native object for this stable entity. */
    public void remove(EntityId id) {
        requireOpen();
        Body body = bodies.remove(id);
        if (body != null) dispose(body);
    }

    /** Returns at most maxHits, nearest first with stable entity tie-breaking. */
    public List<BulletHit> raycast(Vec3 from, Vec3 to, int maxHits) {
        requireOpen();
        if (maxHits < 1 || maxHits > maxBodies) throw new IllegalArgumentException("maxHits exceeds world limit");
        Vector3 start = vector(from);
        Vector3 end = vector(to);
        List<BulletHit> hits = new ArrayList<>();
        for (var entry : bodies.entrySet()) {
            var callback = new ClosestRayResultCallback(start, end);
            try {
                Body body = entry.getValue();
                btCollisionWorld.rayTestSingle(new Matrix4().setToTranslation(start),
                        new Matrix4().setToTranslation(end), body.object(), body.shape(),
                        body.object().getWorldTransform(), callback);
                if (callback.hasHit()) {
                    var normal = new Vector3();
                    var point = new Vector3();
                    callback.getHitNormalWorld(normal);
                    callback.getHitPointWorld(point);
                    hits.add(new BulletHit(entry.getKey(), callback.getClosestHitFraction(), copy(point), copy(normal)));
                }
            } finally {
                callback.dispose();
            }
        }
        hits.sort(Comparator.comparingDouble(BulletHit::fraction).thenComparing(BulletHit::entity));
        return List.copyOf(hits.subList(0, Math.min(maxHits, hits.size())));
    }

    /** Sweeps a Y-up capsule by its centre. Height includes both hemispheres. Tangent hits are ignored. */
    public Optional<BulletHit> sweepCapsule(Vec3 from, Vec3 to, double radius, double height) {
        requireOpen();
        validateCapsule(radius, height);
        Vector3 start = vector(from);
        Vector3 end = vector(to);
        var shape = new btCapsuleShape((float) radius, (float) (height - 2 * radius));
        BulletHit nearest = null;
        Vector3 direction = new Vector3(end).sub(start);
        try {
            for (var entry : bodies.entrySet()) {
                var callback = new ClosestConvexResultCallback(start, end);
                try {
                    Body body = entry.getValue();
                    btCollisionWorld.objectQuerySingle(shape, new Matrix4().setToTranslation(start),
                            new Matrix4().setToTranslation(end), body.object(), body.shape(),
                            body.object().getWorldTransform(), callback, 0);
                    if (callback.hasHit()) {
                        var normal = new Vector3();
                        var point = new Vector3();
                        callback.getHitNormalWorld(normal);
                        callback.getHitPointWorld(point);
                        double fraction = callback.getClosestHitFraction();
                        if (normal.dot(direction) < -1e-7 && (nearest == null || fraction < nearest.fraction())) {
                            nearest = new BulletHit(entry.getKey(), fraction, copy(point), copy(normal));
                        }
                    }
                } finally {
                    callback.dispose();
                }
            }
        } finally {
            shape.dispose();
        }
        return Optional.ofNullable(nearest);
    }

    /** Copies the deepest overlap correction, bounded by the number of registered boxes. */
    public Vec3 capsulePenetration(Vec3 centre, double radius, double height) {
        requireOpen();
        validateCapsule(radius, height);
        var shape = new btCapsuleShape((float) radius, (float) (height - 2 * radius));
        var probe = new btCollisionObject();
        var contact = new Penetration();
        try {
            probe.setCollisionShape(shape);
            probe.setWorldTransform(new Matrix4().setToTranslation(vector(centre)));
            for (Body body : bodies.values()) world.contactPairTest(probe, body.object(), contact);
            return copy(contact.correction);
        } finally {
            contact.dispose();
            probe.dispose();
            shape.dispose();
        }
    }

    private static final class Penetration extends ContactResultCallback {
        private double deepest;
        private final Vector3 correction = new Vector3();
        @Override public float addSingleResult(btManifoldPoint point,
                btCollisionObjectWrapper first, int part0, int index0,
                btCollisionObjectWrapper second, int part1, int index1) {
            double depth = -point.getDistance();
            if (depth > deepest + 1e-6) {
                deepest = depth;
                point.getNormalWorldOnB(correction);
                correction.scl((float) (depth + 0.001));
            }
            return 0;
        }
    }

    private static void validateCapsule(double radius, double height) {
        if (!Double.isFinite(radius) || !Double.isFinite(height) || radius < 0.01
                || height < radius * 2 || height > 100) throw new IllegalArgumentException("capsule dimensions");
    }
    static Vector3 vector(Vec3 value) {
        Objects.requireNonNull(value, "vector");
        if (Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z()))) > 1e6) {
            throw new IllegalArgumentException("world coordinates exceed 1e6 metres");
        }
        return new Vector3((float) value.x(), (float) value.y(), (float) value.z());
    }
    static Vec3 copy(Vector3 vector) { return new Vec3(vector.x, vector.y, vector.z); }
    private static Matrix4 matrix(Transform3D pose) {
        var q = pose.rotation();
        return new Matrix4(vector(pose.position()), new Quaternion((float) q.x(), (float) q.y(),
                (float) q.z(), (float) q.w()), new Vector3(1, 1, 1));
    }
    void requireOpen() {
        if (Thread.currentThread() != owner) throw GameplayException.validation(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION, "access-bullet-world", "construction thread", "different thread", "Use the owner thread.");
        if (closed) throw GameplayException.validation(GameplayDiagnosticCode.WORLD_CLOSED, "access-bullet-world", "open world", "closed world", "Create a new world.");
    }
    private void dispose(Body body) {
        world.removeCollisionObject(body.object());
        body.object().dispose();
        body.shape().dispose();
    }
    @Override public void close() {
        if (Thread.currentThread() != owner) throw GameplayException.validation(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION, "access-bullet-world", "construction thread", "different thread", "Use the owner thread.");
        if (closed) return;
        bodies.values().forEach(this::dispose);
        bodies.clear();
        world.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        configuration.dispose();
        closed = true;
    }
    private record Body(btCollisionObject object, btBoxShape shape, Vec3 scale) { }
}
