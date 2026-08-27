# Box2D 3 integration

The application owns one opaque `GameplayBox2dWorld`. Create it on the render/owner thread from a
`Box2dWorldSpec`, then construct `GameplayBox2dBridge` with a `Box2dBodyFactory`, immutable
`Box2dUnitConversion`, application-owned runtime, and gameplay limits. Register the bridge as a
lifecycle participant and add all three returned systems. Close the bridge before the world.
Construct one `Box2dBodyFactory` per bridge on that same owner thread. The factory owns reusable
native definition/geometry scratch and may be claimed by exactly one bridge; cross-thread or
second-bridge reuse is rejected before registration or scratch mutation.

`GameplayBox2dWorld` fixes the native worker count at zero and steps with the bounded `subStepCount`
from its specification. It never exposes `b2WorldId`. The bridge's native `b2BodyId`, `b2ShapeId`,
`b2JointId`, structs, pointers, callback data, and closures are likewise private implementation
details. Application code uses only immutable copied values.

Resolve each activated entity to a complete `Box2dBodySpec`. Static and kinematic shapes may use
zero density; dynamic shapes require positive density. Friction, restitution, damping, gravity
scale, bullet state, and fixed rotation are copied once into native definitions. `Collider.Shape`
supports `BOX`, equal-dimension `CIRCLE`, and genuine `CAPSULE`. Capsule size is its complete local
bounds: the smaller dimension is the diameter and the larger dimension is the end-to-end length.
Equal capsule dimensions are rejected; use `CIRCLE` instead.

All original double shape relationships are validated before narrowing, and every converted
dimension, half-extent, radius, and capsule centre half-segment must remain finite and strictly
positive.

The bridge applies movement intent in `PRE_PHYSICS`, performs exactly one Box2D 3 step in `PHYSICS`,
and copies position, velocity, angle, angular velocity, mass, and inertia in `POST_PHYSICS`. Read
those facts with `bodyState(entityId)`. Contact begin/hit/end arrays are copied after the step and
normalized by stable fixture ID. `CollisionImpact.normalImpulse` is the maximum positive
`b2ManifoldPoint.totalNormalImpulse`, accumulated across substeps; callback pointers and contact
buffers never escape the capture.

Create joints with `Box2dRevoluteJointSpec`, configure them with `Box2dRevoluteMotor`, inspect copied
state with `revoluteJointState`, and remove them by stable `Box2dJointId`. Joint anchors use render
units; angular values and torque use radians and SI units. Apply physical operations without native
identity:

```java
bridge.applyForceToCenter(torsoId, new Vec2(forceXNewtons, forceYNewtons));
bridge.applyTorque(torsoId, torqueNewtonMetres);
bridge.applyForce(torsoId, new Vec2(forceXNewtons, forceYNewtons), worldPointRenderUnits);
```

For bounded spatial evidence, call `raycast(new Box2dRaycastSpec(...))`. `maxHits` is 1 through 64;
unknown shapes are ignored and copied hits are returned immutable, ordered by fraction then fixture
ID. The call-scoped native closure is released on success and failure.

All bridge/world operations are owner-thread confined. Native mutation rejects closed, stale,
locked, missing, inactive, or wrong-body-type targets. Joint registrations are closed and joints are
destroyed before endpoint shapes and bodies. The bridge releases contact scratch and inspection
registrations but never closes the application-owned world; the application closes
`GameplayBox2dWorld` afterward. Rebuild both at the reset boundary when exact replay reset is
required.

## Migrating from gameplay 0.3

Gameplay 1.0 is a clean backend break. Replace the legacy `World` constructor input with
`GameplayBox2dWorld`, `BodyDef.BodyType` with `Box2dBodyType`, and body-type lambdas with a complete
`Box2dBodySpecResolver`. Remove `Box2dSolverSettings` and contact-listener installation; configure
substeps and hit threshold through `Box2dWorldSpec`. Read
`positionRenderUnits`/`velocityRenderUnitsPerSecond` from the expanded copied state. Applications
must close the bridge before the application-owned world and include the official
`gdx-box2d:3.1.1-0` desktop native. Runtime inspection requires `agent-runtime-box2d:3.0.0`.
