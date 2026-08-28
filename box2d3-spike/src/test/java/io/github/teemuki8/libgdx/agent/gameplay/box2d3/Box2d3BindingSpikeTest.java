package io.github.teemuki8.libgdx.agent.gameplay.box2d3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.enums.b2BodyType;
import com.badlogic.gdx.box2d.enums.b2ShapeType;
import com.badlogic.gdx.box2d.structs.b2BodyDef;
import com.badlogic.gdx.box2d.structs.b2BodyId;
import com.badlogic.gdx.box2d.structs.b2Capsule;
import com.badlogic.gdx.box2d.structs.b2ContactData;
import com.badlogic.gdx.box2d.structs.b2ContactEvents;
import com.badlogic.gdx.box2d.structs.b2ContactHitEvent;
import com.badlogic.gdx.box2d.structs.b2JointId;
import com.badlogic.gdx.box2d.structs.b2Manifold;
import com.badlogic.gdx.box2d.structs.b2ManifoldPoint;
import com.badlogic.gdx.box2d.structs.b2Polygon;
import com.badlogic.gdx.box2d.structs.b2QueryFilter;
import com.badlogic.gdx.box2d.structs.b2RevoluteJointDef;
import com.badlogic.gdx.box2d.structs.b2ShapeDef;
import com.badlogic.gdx.box2d.structs.b2ShapeId;
import com.badlogic.gdx.box2d.structs.b2TreeStats;
import com.badlogic.gdx.box2d.structs.b2Vec2;
import com.badlogic.gdx.box2d.structs.b2WorldDef;
import com.badlogic.gdx.box2d.structs.b2WorldId;
import com.badlogic.gdx.jnigen.runtime.closure.ClosureObject;
import com.badlogic.gdx.jnigen.runtime.pointer.VoidPointer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class Box2d3BindingSpikeTest {
    private static final float STEP_SECONDS = 1.0f / 60.0f;
    private static final int SUB_STEP_COUNT = 4;
    private static final int MAX_FALL_STEPS = 180;
    private static final int MAX_SEPARATION_STEPS = 120;

    private static final List<String> EXPECTED_FACTS = List.of(
            "capsule=-0x1.0p-1,0x1.0p-1,0x1.0p-2",
            "dynamics=0x1.9bbf4cp-8,0x1.7e5cc6p1,0x1.2d9e2ap-3,"
                    + "0x1.79ba18p-1,0x1.6487eep0,0x1.066e4p-2",
            "force-paths=0x1.261aecp-3,0x1.8f902p-3,"
                    + "0x1.261aecp-4,0x1.2bac2p-2",
            "ray=capsule:-0x1.f8bc62p-3,0x1.7e5cc6p1,-0x1.fa752ap-1,"
                    + "-0x1.2c85cp-3,0x1.c0e872p-2",
            "begin=capsule,ground",
            "hit=capsule,ground:0x1.92564p-2,-0x1.27624p-5,"
                    + "0x0.0p0,0x1.0p0,0x1.ad8e7cp2",
            "normal-impulses=0x0.0p0,0x1.51e23p4",
            "end=capsule,ground",
            "lifecycle=joint:false,shape:false,bodies:false,world:false,"
                    + "closure:freed,contact-buffer:freed");

    @BeforeAll
    static void initializeOfficialBinding() {
        Box2d.initialize();
    }

    @Test
    void officialBindingProvesVerticalCopiedFactsAndExplicitLifecycles() {
        List<String> baseline = runScenario();
        assertEquals(EXPECTED_FACTS, baseline);

        for (int repeat = 0; repeat < 2; repeat++) {
            assertEquals(baseline, runScenario());
        }
    }

    private static List<String> runScenario() {
        b2WorldDef worldDef = Box2d.b2DefaultWorldDef();
        worldDef.gravity().x(0.0f);
        worldDef.gravity().y(-10.0f);
        worldDef.workerCount(0);
        worldDef.hitEventThreshold(0.1f);
        b2WorldId worldId = Box2d.b2CreateWorld(worldDef.asPointer());

        b2BodyId dynamicBody = null;
        b2BodyId groundBody = null;
        b2ShapeId capsuleShape = null;
        b2ShapeId groundShape = null;
        b2JointId jointId = null;
        try {
            dynamicBody = createDynamicBody(worldId);
            capsuleShape = createCapsule(dynamicBody);
            groundBody = createGroundBody(worldId);
            groundShape = createGroundShape(groundBody);

            assertEquals(b2ShapeType.b2_capsuleShape, Box2d.b2Shape_GetType(capsuleShape));
            b2Capsule copiedCapsule = Box2d.b2Shape_GetCapsule(capsuleShape);
            assertEquals(-0.5f, copiedCapsule.center1().y());
            assertEquals(0.5f, copiedCapsule.center2().y());
            assertEquals(0.25f, copiedCapsule.radius());

            jointId = createAndDestroyRevoluteJoint(worldId, groundBody, dynamicBody);
            assertFalse(Box2d.b2Joint_IsValid(jointId));

            ForceFact forceFact = applyAllForcePaths(worldId, dynamicBody);

            float mass = Box2d.b2Body_GetMass(dynamicBody);
            float inertia = Box2d.b2Body_GetRotationalInertia(dynamicBody);
            b2Vec2 position = Box2d.b2Body_GetPosition(dynamicBody);
            float angle = Box2d.b2Rot_GetAngle(Box2d.b2Body_GetRotation(dynamicBody));
            float angularVelocity = Box2d.b2Body_GetAngularVelocity(dynamicBody);
            assertTrue(mass > 0.0f);
            assertTrue(inertia > 0.0f);
            assertTrue(position.x() > 0.0f);
            assertTrue(position.y() < 3.0f);
            assertTrue(angle > 0.125f);
            assertTrue(angularVelocity > 0.25f);

            RayFact rayFact = castRayAndCloseClosure(worldId, capsuleShape, position);
            ContactFact contactFact = fallCopyContactsAndSeparate(
                    worldId, dynamicBody, capsuleShape, groundShape);

            ShapeKey capsuleKey = ShapeKey.copyOf(capsuleShape);
            ShapeKey groundKey = ShapeKey.copyOf(groundShape);
            assertTrue(Box2d.b2Shape_IsValid(capsuleShape));
            Box2d.b2DestroyShape(capsuleShape, true);
            assertFalse(Box2d.b2Shape_IsValid(capsuleShape));

            assertTrue(Box2d.b2Body_IsValid(dynamicBody));
            Box2d.b2DestroyBody(dynamicBody);
            assertFalse(Box2d.b2Body_IsValid(dynamicBody));

            assertTrue(Box2d.b2Body_IsValid(groundBody));
            Box2d.b2DestroyBody(groundBody);
            assertFalse(Box2d.b2Body_IsValid(groundBody));
            assertFalse(Box2d.b2Shape_IsValid(groundShape));

            assertTrue(Box2d.b2World_IsValid(worldId));
            Box2d.b2DestroyWorld(worldId);
            assertFalse(Box2d.b2World_IsValid(worldId));

            return List.of(
                    "capsule=" + floats(copiedCapsule.center1().y(),
                            copiedCapsule.center2().y(), copiedCapsule.radius()),
                    "dynamics=" + floats(position.x(), position.y(), angle,
                            angularVelocity, mass, inertia),
                    "force-paths=" + forceFact.serialize(),
                    "ray=" + rayFact.serialize(capsuleKey),
                    "begin=" + contactFact.beginPair().serialize(capsuleKey, groundKey),
                    "hit=" + contactFact.hitPair().serialize(capsuleKey, groundKey)
                            + ":" + floats(contactFact.hitPointX(), contactFact.hitPointY(),
                                    contactFact.hitNormalX(), contactFact.hitNormalY(),
                                    contactFact.approachSpeed()),
                    "normal-impulses=" + floats(
                            contactFact.maximumLastSubStepNormalImpulse(),
                            contactFact.maximumTotalNormalImpulse()),
                    "end=" + contactFact.endPair().serialize(capsuleKey, groundKey),
                    "lifecycle=joint:false,shape:false,bodies:false,world:false,closure:freed,"
                            + "contact-buffer:freed");
        } finally {
            if (jointId != null && Box2d.b2Joint_IsValid(jointId)) {
                Box2d.b2DestroyJoint(jointId);
            }
            if (capsuleShape != null && Box2d.b2Shape_IsValid(capsuleShape)) {
                Box2d.b2DestroyShape(capsuleShape, true);
            }
            if (dynamicBody != null && Box2d.b2Body_IsValid(dynamicBody)) {
                Box2d.b2DestroyBody(dynamicBody);
            }
            if (groundBody != null && Box2d.b2Body_IsValid(groundBody)) {
                Box2d.b2DestroyBody(groundBody);
            }
            if (Box2d.b2World_IsValid(worldId)) {
                Box2d.b2DestroyWorld(worldId);
            }
        }
    }

    private static b2BodyId createDynamicBody(b2WorldId worldId) {
        b2BodyDef bodyDef = Box2d.b2DefaultBodyDef();
        bodyDef.type(b2BodyType.b2_dynamicBody);
        bodyDef.position().x(0.0f);
        bodyDef.position().y(3.0f);
        bodyDef.setRotation(Box2d.b2MakeRot(0.125f));
        bodyDef.angularVelocity(0.25f);
        bodyDef.enableSleep(false);
        return Box2d.b2CreateBody(worldId, bodyDef.asPointer());
    }

    private static b2ShapeId createCapsule(b2BodyId bodyId) {
        b2ShapeDef shapeDef = Box2d.b2DefaultShapeDef();
        shapeDef.density(2.0f);
        shapeDef.material().friction(0.4f);
        shapeDef.enableContactEvents(true);
        shapeDef.enableHitEvents(true);

        b2Capsule capsule = new b2Capsule();
        capsule.center1().x(0.0f);
        capsule.center1().y(-0.5f);
        capsule.center2().x(0.0f);
        capsule.center2().y(0.5f);
        capsule.radius(0.25f);
        return Box2d.b2CreateCapsuleShape(bodyId, shapeDef.asPointer(), capsule.asPointer());
    }

    private static b2BodyId createGroundBody(b2WorldId worldId) {
        b2BodyDef bodyDef = Box2d.b2DefaultBodyDef();
        bodyDef.position().x(0.0f);
        bodyDef.position().y(-0.5f);
        return Box2d.b2CreateBody(worldId, bodyDef.asPointer());
    }

    private static b2ShapeId createGroundShape(b2BodyId groundBody) {
        b2ShapeDef shapeDef = Box2d.b2DefaultShapeDef();
        shapeDef.material().friction(0.4f);
        b2Polygon groundBox = Box2d.b2MakeBox(10.0f, 0.5f);
        return Box2d.b2CreatePolygonShape(
                groundBody, shapeDef.asPointer(), groundBox.asPointer());
    }

    private static b2JointId createAndDestroyRevoluteJoint(
            b2WorldId worldId, b2BodyId groundBody, b2BodyId dynamicBody) {
        b2Vec2 worldAnchor = vector(0.0f, 3.0f);
        b2RevoluteJointDef jointDef = Box2d.b2DefaultRevoluteJointDef();
        jointDef.setBodyIdA(groundBody);
        jointDef.setBodyIdB(dynamicBody);
        jointDef.setLocalAnchorA(Box2d.b2Body_GetLocalPoint(groundBody, worldAnchor));
        jointDef.setLocalAnchorB(Box2d.b2Body_GetLocalPoint(dynamicBody, worldAnchor));
        jointDef.enableLimit(true);
        jointDef.lowerAngle(-0.5f);
        jointDef.upperAngle(0.5f);
        jointDef.collideConnected(false);
        b2JointId jointId = Box2d.b2CreateRevoluteJoint(worldId, jointDef.asPointer());
        assertTrue(Box2d.b2Joint_IsValid(jointId));
        Box2d.b2DestroyJoint(jointId);
        return jointId;
    }

    private static ForceFact applyAllForcePaths(b2WorldId worldId, b2BodyId bodyId) {
        b2Vec2 velocityBeforeCentreForce = Box2d.b2Body_GetLinearVelocity(bodyId);
        Box2d.b2Body_ApplyForceToCenter(bodyId, vector(12.0f, 0.0f), true);
        Box2d.b2World_Step(worldId, STEP_SECONDS, SUB_STEP_COUNT);
        b2Vec2 velocityAfterCentreForce = Box2d.b2Body_GetLinearVelocity(bodyId);
        float centreForceVelocityDelta =
                velocityAfterCentreForce.x() - velocityBeforeCentreForce.x();
        assertTrue(centreForceVelocityDelta > 0.0f);

        float angularVelocityBeforeTorque = Box2d.b2Body_GetAngularVelocity(bodyId);
        Box2d.b2Body_ApplyTorque(bodyId, 3.0f, true);
        Box2d.b2World_Step(worldId, STEP_SECONDS, SUB_STEP_COUNT);
        float angularVelocityAfterTorque = Box2d.b2Body_GetAngularVelocity(bodyId);
        float torqueAngularVelocityDelta =
                angularVelocityAfterTorque - angularVelocityBeforeTorque;
        assertTrue(torqueAngularVelocityDelta > 0.0f);

        b2Vec2 velocityBeforePointForce = Box2d.b2Body_GetLinearVelocity(bodyId);
        float angularVelocityBeforePointForce =
                Box2d.b2Body_GetAngularVelocity(bodyId);
        b2Vec2 centre = Box2d.b2Body_GetPosition(bodyId);
        Box2d.b2Body_ApplyForce(
                bodyId, vector(0.0f, 6.0f),
                vector(centre.x() + 0.75f, centre.y()), true);
        Box2d.b2World_Step(worldId, STEP_SECONDS, SUB_STEP_COUNT);
        b2Vec2 velocityAfterPointForce = Box2d.b2Body_GetLinearVelocity(bodyId);
        float pointForceVelocityDelta = velocityAfterPointForce.y()
                - velocityBeforePointForce.y() + 10.0f * STEP_SECONDS;
        float pointForceAngularVelocityDelta =
                Box2d.b2Body_GetAngularVelocity(bodyId)
                        - angularVelocityBeforePointForce;
        assertTrue(pointForceVelocityDelta > 0.0f);
        assertTrue(pointForceAngularVelocityDelta > 0.0f);
        return new ForceFact(
                centreForceVelocityDelta,
                torqueAngularVelocityDelta,
                pointForceVelocityDelta,
                pointForceAngularVelocityDelta);
    }

    private static RayFact castRayAndCloseClosure(
            b2WorldId worldId, b2ShapeId expectedShape, b2Vec2 bodyPosition) {
        ShapeKey expectedKey = ShapeKey.copyOf(expectedShape);
        List<RayFact> hits = new ArrayList<>();
        ClosureObject<Box2d.b2CastResultFcn> closure = ClosureObject.fromClosure(
                (shapeId, point, normal, fraction, context) -> {
                    hits.add(new RayFact(
                            ShapeKey.copyOf(shapeId), point.x(), point.y(),
                            normal.x(), normal.y(), fraction));
                    return 1.0f;
                });
        try {
            b2Vec2 origin = vector(-2.0f, bodyPosition.y());
            b2Vec2 translation = vector(4.0f, 0.0f);
            b2QueryFilter filter = Box2d.b2DefaultQueryFilter();
            b2TreeStats stats = new b2TreeStats();
            Box2d.b2World_CastRay(
                    worldId, origin, translation, filter, closure,
                    new VoidPointer(0L, false), stats);
            assertTrue(stats.leafVisits() > 0);
        } finally {
            closure.free();
        }
        RayFact capsuleHit = hits.stream()
                .filter(hit -> hit.shapeKey().equals(expectedKey))
                .min(Comparator.comparingDouble(RayFact::fraction))
                .orElseThrow();
        assertTrue(capsuleHit.fraction() > 0.0f);
        assertTrue(capsuleHit.fraction() < 1.0f);
        return capsuleHit;
    }

    private static ContactFact fallCopyContactsAndSeparate(
            b2WorldId worldId,
            b2BodyId dynamicBody,
            b2ShapeId capsuleShape,
            b2ShapeId groundShape) {
        ShapeKey capsuleKey = ShapeKey.copyOf(capsuleShape);
        ShapeKey groundKey = ShapeKey.copyOf(groundShape);
        ShapePair beginPair = null;
        ShapePair hitPair = null;
        float hitPointX = Float.NaN;
        float hitPointY = Float.NaN;
        float hitNormalX = Float.NaN;
        float hitNormalY = Float.NaN;
        float approachSpeed = Float.NaN;
        ImpulseFact impulses = null;
        b2ContactEvents events = new b2ContactEvents();

        for (int step = 0; step < MAX_FALL_STEPS && hitPair == null; step++) {
            Box2d.b2World_Step(worldId, STEP_SECONDS, SUB_STEP_COUNT);
            Box2d.b2World_GetContactEvents(worldId, events);
            if (beginPair == null && events.beginCount() > 0) {
                var event = events.beginEvents().asStackElement(0);
                beginPair = ShapePair.copyOf(event.shapeIdA(), event.shapeIdB());
            }
            if (events.hitCount() > 0) {
                b2ContactHitEvent event = events.hitEvents().asStackElement(0);
                hitPair = ShapePair.copyOf(event.shapeIdA(), event.shapeIdB());
                hitPointX = event.point().x();
                hitPointY = event.point().y();
                hitNormalX = event.normal().x();
                hitNormalY = event.normal().y();
                approachSpeed = event.approachSpeed();
                impulses = copyMatchingNormalImpulses(event);
            }
        }

        assertNotNull(beginPair);
        assertNotNull(hitPair);
        assertEquals(ShapePair.sorted(capsuleKey, groundKey), beginPair.sorted());
        assertEquals(ShapePair.sorted(capsuleKey, groundKey), hitPair.sorted());
        assertTrue(approachSpeed > 0.0f);
        assertNotNull(impulses);
        assertTrue(impulses.maximumTotalNormalImpulse() > 0.0f);

        Box2d.b2Body_SetLinearVelocity(dynamicBody, vector(0.0f, 12.0f));
        ShapePair endPair = null;
        for (int step = 0; step < MAX_SEPARATION_STEPS && endPair == null; step++) {
            Box2d.b2World_Step(worldId, STEP_SECONDS, SUB_STEP_COUNT);
            Box2d.b2World_GetContactEvents(worldId, events);
            if (events.endCount() > 0) {
                var event = events.endEvents().asStackElement(0);
                endPair = ShapePair.copyOf(event.shapeIdA(), event.shapeIdB());
            }
        }
        assertNotNull(endPair);
        assertEquals(ShapePair.sorted(capsuleKey, groundKey), endPair.sorted());
        return new ContactFact(
                beginPair, hitPair, endPair, hitPointX, hitPointY,
                hitNormalX, hitNormalY, approachSpeed,
                impulses.maximumLastSubStepNormalImpulse(),
                impulses.maximumTotalNormalImpulse());
    }

    private static ImpulseFact copyMatchingNormalImpulses(b2ContactHitEvent hitEvent) {
        int capacity = Box2d.b2Shape_GetContactCapacity(hitEvent.shapeIdA());
        assertTrue(capacity > 0);
        var buffer = new b2ContactData.b2ContactDataPointer(capacity, false);
        try {
            int count = Box2d.b2Shape_GetContactData(
                    hitEvent.shapeIdA(), buffer, capacity);
            assertTrue(count > 0);
            assertTrue(count <= capacity);
            ShapePair hitPair = ShapePair.copyOf(hitEvent.shapeIdA(), hitEvent.shapeIdB());
            float maximumLastSubStep = 0.0f;
            float maximumTotal = 0.0f;
            boolean matched = false;
            for (int contactIndex = 0; contactIndex < count; contactIndex++) {
                b2ContactData contact = buffer.asStackElement(contactIndex);
                if (!ShapePair.copyOf(contact.shapeIdA(), contact.shapeIdB())
                        .sorted().equals(hitPair.sorted())) {
                    continue;
                }
                matched = true;
                b2Manifold manifold = contact.manifold();
                for (int pointIndex = 0; pointIndex < manifold.pointCount(); pointIndex++) {
                    b2ManifoldPoint point = manifold.points().asStackElement(pointIndex);
                    maximumLastSubStep = Math.max(
                            maximumLastSubStep, point.normalImpulse());
                    maximumTotal = Math.max(
                            maximumTotal, point.totalNormalImpulse());
                }
            }
            assertTrue(matched);
            return new ImpulseFact(maximumLastSubStep, maximumTotal);
        } finally {
            buffer.free();
            assertTrue(buffer.isFreed());
        }
    }

    private static b2Vec2 vector(float x, float y) {
        b2Vec2 result = new b2Vec2();
        result.x(x);
        result.y(y);
        return result;
    }

    private static String floats(float... values) {
        List<String> serialized = new ArrayList<>(values.length);
        for (float value : values) {
            serialized.add(exact(value));
        }
        return String.join(",", serialized);
    }

    private static String exact(float value) {
        return Float.toHexString(value);
    }

    private record ShapeKey(int index, int world, int generation) {
        static ShapeKey copyOf(b2ShapeId shapeId) {
            return new ShapeKey(shapeId.index1(), shapeId.world0(), shapeId.generation());
        }
    }

    private record ShapePair(ShapeKey first, ShapeKey second) {
        static ShapePair copyOf(b2ShapeId first, b2ShapeId second) {
            return new ShapePair(ShapeKey.copyOf(first), ShapeKey.copyOf(second));
        }

        static ShapePair sorted(ShapeKey first, ShapeKey second) {
            return compare(first, second) <= 0
                    ? new ShapePair(first, second)
                    : new ShapePair(second, first);
        }

        ShapePair sorted() {
            return sorted(first, second);
        }

        String serialize(ShapeKey capsule, ShapeKey ground) {
            List<String> labels = new ArrayList<>(2);
            labels.add(label(first, capsule, ground));
            labels.add(label(second, capsule, ground));
            labels.sort(String::compareTo);
            return String.join(",", labels);
        }

        private static String label(ShapeKey key, ShapeKey capsule, ShapeKey ground) {
            if (key.equals(capsule)) {
                return "capsule";
            }
            if (key.equals(ground)) {
                return "ground";
            }
            throw new AssertionError("unexpected native shape ID");
        }

        private static int compare(ShapeKey left, ShapeKey right) {
            int byIndex = Integer.compare(left.index(), right.index());
            if (byIndex != 0) {
                return byIndex;
            }
            int byWorld = Integer.compare(left.world(), right.world());
            return byWorld != 0
                    ? byWorld
                    : Integer.compare(left.generation(), right.generation());
        }
    }

    private record ForceFact(
            float centreForceVelocityDelta,
            float torqueAngularVelocityDelta,
            float pointForceVelocityDelta,
            float pointForceAngularVelocityDelta) {
        String serialize() {
            return floats(
                    centreForceVelocityDelta,
                    torqueAngularVelocityDelta,
                    pointForceVelocityDelta,
                    pointForceAngularVelocityDelta);
        }
    }

    private record RayFact(
            ShapeKey shapeKey,
            float pointX,
            float pointY,
            float normalX,
            float normalY,
            float fraction) {
        String serialize(ShapeKey capsuleKey) {
            assertEquals(capsuleKey, shapeKey);
            return "capsule:" + floats(pointX, pointY, normalX, normalY, fraction);
        }
    }

    private record ImpulseFact(
            float maximumLastSubStepNormalImpulse,
            float maximumTotalNormalImpulse) {
    }

    private record ContactFact(
            ShapePair beginPair,
            ShapePair hitPair,
            ShapePair endPair,
            float hitPointX,
            float hitPointY,
            float hitNormalX,
            float hitNormalY,
            float approachSpeed,
            float maximumLastSubStepNormalImpulse,
            float maximumTotalNormalImpulse) {
    }
}
