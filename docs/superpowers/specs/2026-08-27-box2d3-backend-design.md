# Box2D 3 backend migration design

## Context

The teemuki8 stack currently uses libGDX's legacy object-oriented Box2D binding through `com.badlogic.gdx.physics.box2d` at version 1.14.2. Public and internal APIs depend on `World`, `Body`, `Fixture`, `Joint`, `BodyDef.BodyType`, callbacks, and the legacy `world.step(delta, velocityIterations, positionIterations)` model.

libGDX now publishes an official standalone Box2D 3 binding from `libgdx/gdx-box2d`:

```text
com.badlogicgames.gdx:gdx-box2d:3.1.1-0
com.badlogicgames.gdx:gdx-box2d-platform:3.1.1-0:natives-desktop
package com.badlogic.gdx.box2d
native Box2D 3.1.1
```

It exposes handle/struct APIs including `b2WorldId`, `b2BodyId`, `b2ShapeId`, `b2JointId`, `b2Capsule`, `b2CreateCapsuleShape`, body/contact events, ray/shape casts, explicit body/shape definitions, mass properties, and the complete Box2D 3 joint set. It is not a drop-in replacement for the legacy binding.

This design performs a clean coordinated migration rather than emulating capsules through compound legacy fixtures. libGDX core remains 1.14.2 for application/rendering APIs; the independently versioned physics binding becomes 3.1.1-0.

## Goals

1. Replace legacy native object ownership with private Box2D 3 handles.
2. Add genuine capsule colliders.
3. Expose backend-neutral copied body type/dynamics/material/raycast facts.
4. Preserve application ownership, render-thread confinement, fixed-step authority, bounded evidence, deterministic ordering, lifecycle barriers, and no native identity escape from gameplay.
5. Preserve gameplay event semantics, including normal collision impulses, despite Box2D 3's event model.
6. Migrate runtime inspection before gameplay consumes it.
7. Keep existing released legacy artifacts usable; perform breaking changes only in new major versions.

## Non-goals

- No simultaneous legacy and Box2D 3 backend inside one running application.
- No adapter that exposes native IDs to gameplay consumers.
- No reflection, arbitrary native calls, caller-provided pointers, native callbacks over MCP, or unrestricted queries.
- No Maven Local, snapshots, composite builds, or Git dependencies in released consumers.
- No libGDX core upgrade solely for physics.
- No automatic Maven Central publication. Implementation branches/PRs are authorized; every runtime/gameplay release remains a separate irreversible authorization.

## Version and release strategy

- `libgdx-agent-runtime` target: **3.0.0**. `agent-runtime-box2d` becomes Box2D 3 based and intentionally breaks its legacy native registration API. Other runtime modules retain behavior but align to the major.
- `libgdx-agent-gameplay` target: **1.0.0**. `gameplay-box2d` becomes Box2D 3 based and removes legacy libGDX types from its public API.
- `ragduel` and `libgdx-agent-bootstrap` migrate only after both public releases resolve from Maven Central.

No compatibility shim, dual backend, deprecated alias, or alternate module is retained. The old runtime 2.x/gameplay 0.x coordinates remain immutable for legacy consumers.

## Dependency graph

```text
libGDX core 1.14.2

agent-runtime-box2d 3.0.0
    -> gdx-box2d 3.1.1-0
    -> agent-runtime-core 3.0.0

gameplay-box2d 1.0.0
    -> gdx-box2d 3.1.1-0
    -> gameplay-core 1.0.0
    -> agent-runtime-box2d 3.0.0

ragduel
    -> gameplay-* 1.0.0
    -> agent-runtime-* 3.0.0
```

The standalone Box2D binding uses `com.badlogic.gdx.box2d` and jnigen runtime dependencies. Legacy `com.badlogic.gdx.physics.box2d` is absent from runtime 3/gameplay 1 dependency graphs and source.

## Backend-neutral public gameplay values

### Body type

Replace `BodyDef.BodyType` in public values with:

```java
public enum Box2dBodyType {
    STATIC,
    KINEMATIC,
    DYNAMIC
}
```

### World ownership

The application owns one opaque wrapper:

```java
public final class GameplayBox2dWorld implements AutoCloseable {
    public static GameplayBox2dWorld create(Box2dWorldSpec spec);
    public boolean isClosed();
    @Override public void close();
}

public record Box2dWorldSpec(
        Vec2 gravityMetresPerSecondSquared,
        int subStepCount,
        double hitEventThresholdMetresPerSecond) {}
```

`GameplayBox2dWorld` contains the private `b2WorldId`, task/closure registrations, shape/body maps, and owner thread. Creation occurs on the application render thread. The application closes `GameplayBox2dBridge` first, then closes the world. The wrapper never exposes IDs or pointers.

`subStepCount` is bounded 1..16; the deterministic default is 4. The gameplay world advances one authoritative fixed tick through:

```text
b2World_Step(worldId, fixedDeltaSeconds, subStepCount)
```

### Body specification

Replace the body-type-only resolver with:

```java
public record Box2dBodySpec(
        Box2dBodyType type,
        double densityKilogramsPerSquareMetre,
        double friction,
        double restitution,
        double linearDamping,
        double angularDamping,
        double gravityScale,
        boolean bullet,
        boolean fixedRotation) {}

@FunctionalInterface
public interface Box2dBodySpecResolver {
    Box2dBodySpec resolve(EntityView entity);
}
```

All scalars are finite. Density/damping/gravity are non-negative; friction is non-negative; restitution is [0,1]. Static/kinematic shapes may use zero density. The body factory copies values into `b2BodyDef`/`b2ShapeDef` and retains no caller-mutability.

### Copied state

Replace the legacy state record with:

```java
public record Box2dBodyState(
        EntityId entityId,
        String fixtureId,
        Box2dBodyType bodyType,
        Vec2 positionRenderUnits,
        Vec2 velocityRenderUnitsPerSecond,
        double angleRadians,
        double angularVelocityRadiansPerSecond,
        double massKilograms,
        double rotationalInertiaKilogramMetresSquared,
        Collider.Shape colliderShape,
        Vec2 colliderSize,
        Vec2 colliderOffset,
        boolean sensor,
        boolean active) {}
```

Every value is copied from Box2D 3 through `b2Body_Get*` and `b2Shape_Get*` calls. No struct, pointer, buffer, or handle survives the method boundary.

## Collider schema and native shapes

`Collider.Shape` becomes exactly:

```text
BOX
CIRCLE
CAPSULE
```

- BOX: `b2MakeOffsetBox`/polygon shape.
- CIRCLE: equal width/height; radius half width.
- CAPSULE: `size` describes the complete local axis-aligned bounds. The smaller dimension is diameter; the larger dimension is total end-to-end length. The centre segment endpoints lie on the major axis at `(major/2 - radius)` from the offset. Equal dimensions are rejected with guidance to use CIRCLE.

Vertical and horizontal capsules are supported. Body rotation supplies arbitrary world orientation. Each gameplay entity still owns exactly one Box2D 3 shape ID, so stable fixture IDs and contact ordering remain simple.

## Private identity and native maps

Native IDs are opaque generated structs. Internal deterministic keys copy their scalar identity fields:

```java
record BodyKey(int index1, char world0, char generation) {}
record ShapeKey(int index1, char world0, char generation) {}
record JointKey(int index1, char world0, char generation) {}
```

Gameplay maps stable `EntityId`/`Box2dJointId` to private owned handles and reverse maps shape keys to stable fixture identities. ID validity is checked with Box2D 3 validity functions before every native operation. Destroyed IDs are removed before native destruction. Struct wrappers and closure objects are freed/closed according to jnigen ownership rules.

## Forces, torque, and point force

Preserve:

```java
void applyForceToCenter(EntityId entityId, Vec2 forceNewtons)
void applyTorque(EntityId entityId, double torqueNewtonMetres)
```

Add:

```java
void applyForce(
        EntityId entityId,
        Vec2 forceNewtons,
        Vec2 worldPointRenderUnits)
```

All operations are owner-thread, open-world, unlocked-world, finite, active-body checked, and wake dynamic bodies. Force retains the published legacy semantic allowance for active kinematic targets as a typed accepted native no-op only if Box2D 3 behaves equivalently; otherwise the 1.0 contract explicitly documents dynamic-only behavior and migration diagnostics. Torque and force-at-point require dynamic bodies.

## Revolute joints and future joint values

The application-facing copied revolute APIs remain:

```java
createRevoluteJoint(Box2dRevoluteJointSpec)
configureRevoluteMotor(Box2dJointId, Box2dRevoluteMotor)
revoluteJointState(Box2dJointId)
removeJoint(Box2dJointId)
```

Internally they use `b2RevoluteJointDef`, `b2CreateRevoluteJoint`, and Box2D 3 joint setters/getters. Stable endpoint IDs, limits, collide-connected behavior, motor speed/torque, ordering, bounds, joint-before-body destruction, reset, and inspection entities remain observable contracts.

Wheel/prismatic/distance APIs are deferred until a consumer design requires them. The backend migration must not expose generic native joint creation.

## Contact and collision-impact evidence

Box2D 3 provides begin/end/hit event arrays after a step rather than the legacy listener/post-solve callback.

After every `b2World_Step`:

1. obtain `b2ContactEvents`;
2. copy begin/end shape IDs into stable endpoint facts;
3. for each hit event, copy shape IDs, point, normal, and approach speed immediately;
4. query bounded contact data for the hit shape using `b2Shape_GetContactCapacity` and `b2Shape_GetContactData`;
5. find the contact pair matching the hit's two shapes;
6. copy the maximum positive `b2ManifoldPoint.normalImpulse`;
7. emit existing normalized `CollisionImpact` only when a positive impulse is available;
8. sort by stable fixture IDs and event phase before gameplay event emission.

No event struct, pointer, contact-data buffer, manifold pointer, or shape ID escapes the post-step capture. Contact capacity and total copied facts are bounded by existing gameplay limits; overflow throws typed diagnostics rather than truncating. Tests cover speculative hit events, pair matching, multiple manifold points, ordering, capacity exhaustion, and reset.

The public `CollisionImpact.normalImpulse` remains N·s and the canonical/runtime event contracts remain unchanged.

## Bounded raycast

Add:

```java
public record Box2dRaycastSpec(
        Vec2 originRenderUnits,
        Vec2 translationRenderUnits,
        int categoryBits,
        int maskBits,
        int maxHits) {}

public record Box2dRaycastHit(
        EntityId entityId,
        String fixtureId,
        Vec2 pointRenderUnits,
        Vec2 normal,
        double fraction) {}

List<Box2dRaycastHit> raycast(Box2dRaycastSpec spec)
```

`maxHits` is 1..64. The bridge allocates one bounded callback collector for the call, copies facts immediately, sorts by fraction then fixture ID, and returns immutable values. Unknown/non-gameplay shapes are ignored. Callback closures are always released, including timeout/error paths. No arbitrary query/filter callback enters protocol or public API.

## Runtime Box2D 3 inspection

`agent-runtime-box2d` 3.0 replaces legacy registrations with Box2D 3 registrations:

```java
registerWorld(String id, b2WorldId world)
registerBody(String id, String worldId, b2BodyId body)
registerShape(String id, String bodyId, b2ShapeId shape, Box2dShapeSpec spec)
registerJoint(String id, String worldId, b2JointId joint)
```

These APIs are adapter-facing and may accept native IDs; gameplay consumers never receive them. The adapter copies ID scalars internally, validates liveness, and publishes the same stable runtime entity IDs:

```text
box2d.world.<id>
box2d.body.<id>
box2d.fixture.<id>
box2d.joint.<id>
```

Property names remain compatible where semantics are identical. New properties include angular velocity, mass, inertia, shape type `capsule`, capsule endpoints/radius, and Box2D 3 counters. Removed legacy-only properties require explicit 3.0 migration notes. Unregister children before parents; close closures/registrations before destroying native IDs.

## Gameplay bridge lifecycle

- Application creates `GameplayBox2dWorld`.
- Bridge registers world/contact inspection and creates mapped bodies/shapes during activation.
- PRE_PHYSICS applies copied commands/forces/joint settings.
- PHYSICS performs exactly one `b2World_Step` with bounded substeps.
- POST_PHYSICS copies body transforms/velocities, contact events, impacts, runtime evidence, and gameplay components.
- On entity disposal, destroy connected joints, close inspection registration, destroy shapes/body, and remove reverse keys.
- On reset/close, destroy all joints before bodies, remove contact/world inspection last, free closures/buffers, and leave application world open until caller closes it.

All operations remain owner-thread confined. Completed immutable snapshots may cross threads.

## Determinism and performance

- World worker count remains zero in the first migration to avoid task callback/scheduling nondeterminism.
- Fixed tick uses a stable substep count of 4 unless an application explicitly supplies another bounded value.
- Native event arrays are copied once per step into preallocated bounded collectors.
- Reverse ID maps use scalar keys and deterministic maps; no native struct is used as a map key.
- Hot paths reuse b2Vec2, contact buffers, body-state scratch structs, and event collectors.
- Canonical gameplay encoding depends only on copied stable values, never native handle indices.

## Migration sequence

### Runtime 3.0

1. Add Box2D 3 dependency/natives and strict verification metadata.
2. Port `agent-runtime-box2d` inspection and fixtures.
3. Remove legacy Box2D imports/dependencies from runtime.
4. Run native fixture, Javadocs, API break report, archives/POMs and full gates.
5. Push focused runtime PR. Stop before release until explicitly authorized.

### Gameplay 1.0

1. Consume publicly resolved runtime 3.0.
2. Add backend-neutral public values and application-owned world wrapper.
3. Port body/shape/joint/step/contact/lifecycle internals.
4. Add genuine capsule, body dynamics/materials, point force, and raycast.
5. Port gameplay fixture and deterministic replay evidence.
6. Remove all legacy Box2D imports/dependencies.
7. Run native/full/API break/publication gates.
8. Push focused gameplay PR. Stop before release until explicitly authorized.

### Consumers

After public runtime 3.0 and gameplay 1.0 resolve:

1. upgrade ragduel dependency/locks/verification metadata;
2. remove the temporary gameplay 0.3 composite override;
3. use genuine CAPSULE for torso/hips/limbs/palms/feet where visually appropriate;
4. rerun balance, palm combat, deterministic replay, MCP, screenshots and full checks;
5. upgrade bootstrap baseline/contracts/guides/generated-project tests.

## Verification contracts

### Runtime

- real Box2D 3 world/body/capsule/revolute registration;
- stable runtime IDs and copied values;
- invalid/stale ID, wrong thread, closed runtime, bounds and lifecycle failures;
- no native pointer/struct in runtime values;
- full Linux Xvfb fixture and publication qualification.

### Gameplay

- native capsule dimensions/orientation/inspection;
- copied dynamics match native state without exposing IDs;
- force/torque/point-force direction and wake behavior;
- revolute limits/motors/state/reset;
- begin/end/impact copy-out with exact maximum normal impulse;
- bounded raycast ordering/filtering/overflow/closure cleanup;
- body/shape/joint destruction ordering and application-owned world survival;
- deterministic arena transcript and canonical digests;
- no `com.badlogic.gdx.physics.box2d` imports or legacy artifact locks.

### Ragduel

- 30 genuine capsule/shape-backed fighter bodies and 28 joints;
- exact upright/knockdown/recovery/KO behavior;
- palm-only held-input damage;
- deterministic replay and real MCP invariant proof;
- original-size visual/collider alignment;
- full clean project gate against Central artifacts only.

## Risks

- **Generated binding maturity:** begin with a narrow native lifecycle spike before public API migration.
- **Handle lifetime errors:** centralize scalar key copying/liveness checks and verify stale IDs after destruction.
- **Contact impulse semantic drift:** qualify hit-event/contact-data correlation in real collisions before preserving damage contracts.
- **Runtime/gameplay release dependency:** publish runtime first; never use snapshots in gameplay PR qualification.
- **Large breaking surface:** major versions, complete migration guides, no misleading compatibility aliases.
- **Platform natives:** qualify Linux first and retain existing desktop/mobile/iOS artifact verification; do not claim untested platforms.
- **MCP/tuning churn:** preserve the new test pyramid—copied mechanics for exact physics, deterministic runner for replay/KO, MCP for real input/outcome invariants and presentation.
