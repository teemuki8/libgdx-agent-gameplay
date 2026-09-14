package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btTypedConstraint;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletAdapterLimits;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletContactLimits;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletContactPolicy;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletContacts;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletInspection;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletRegistration;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletWorldSpec;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.HashMap;
import java.util.Map;

/** Private registration lifecycle; no native handles leave the world. */
final class BulletRuntimeObservation implements AutoCloseable {
    private final AgentRuntime runtime;
    private final String worldId;
    private final BulletInspection inspection;
    private final Map<EntityId, BulletRegistration<btRigidBody>> bodies = new HashMap<>();
    private final Map<EntityId, BulletRegistration<btCollisionShape>> shapes = new HashMap<>();
    private final Map<BulletConstraintId, BulletRegistration<btTypedConstraint>> joints = new HashMap<>();
    final BulletContacts contacts;

    BulletRuntimeObservation(AgentRuntime runtime, String worldId, btDiscreteDynamicsWorld world,
            BulletDynamicsLimits limits, BulletContactLimits contactLimits) {
        this.runtime = runtime;
        this.worldId = worldId;
        inspection = new BulletInspection(runtime, new BulletAdapterLimits(1, limits.maxBodies(), limits.maxBodies(),
                Math.max(1, limits.maxConstraints()), Math.max(1, limits.maxConstraints())));
        try {
            inspection.registerWorld(worldId, world, new BulletWorldSpec(1));
            contacts = inspection.registerContacts(worldId, contactLimits, BulletContactPolicy.developmentDefaults());
        } catch (RuntimeException | Error failure) {
            inspection.close();
            throw failure;
        }
    }
    void requireMutable() { runtime.entities().requireProviderMutationAllowed(); }
    void addBody(EntityId id, btRigidBody body, btCollisionShape shape) {
        String stableId = worldId + "." + id.value();
        var registration = inspection.registerBody(stableId, worldId, body);
        try {
            shapes.put(id, inspection.registerShape(stableId, stableId, shape));
            bodies.put(id, registration);
        } catch (RuntimeException | Error failure) {
            registration.close();
            throw failure;
        }
    }
    void addJoint(BulletConstraintId id, btTypedConstraint joint) {
        joints.put(id, inspection.registerJoint(worldId + "." + id.value(), worldId, joint));
    }
    void removeJoint(BulletConstraintId id) {
        var registration = joints.get(id);
        if (registration != null) { registration.close();
            joints.remove(id); }
    }
    void removeBody(EntityId id) {
        var shape = shapes.get(id);
        if (shape != null) { shape.close();
            shapes.remove(id); }
        var body = bodies.get(id);
        if (body != null) { body.close();
            bodies.remove(id); }
    }
    @Override public void close() { inspection.close();
        bodies.clear();
        shapes.clear();
        joints.clear(); }
}
