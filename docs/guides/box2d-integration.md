# Box2D integration

The application owns `com.badlogic.gdx.physics.box2d.World`, native initialization, and the one
installed contact listener. Create `GameplayBox2dBridge` on the world owner thread with one
`Box2dBodyFactory`, immutable `Box2dUnitConversion`, fixed `Box2dSolverSettings`, application-owned
runtime, and gameplay limits. Add the bridge as a lifecycle participant and add all three returned
systems.

The bridge creates and owns only bodies/fixtures corresponding to active gameplay entities plus
revolute joints explicitly created through copied-value specifications. It applies movement intent
in `PRE_PHYSICS`, steps the native world in `PHYSICS`, and copies native position/velocity authority
back in `POST_PHYSICS`. Contact callbacks immediately copy stable fixture endpoints and the maximum
positive normal impulse. Gameplay consumes bounded `CollisionStarted`, `CollisionEnded`, and
`CollisionImpact` events after the step; no callback-owned object is retained.

Native `Body`, `Fixture`, and `Joint` identities never leave the bridge. Use `bodyState(entityId)`
and `revoluteJointState(jointId)` for immutable copied state. Create joints with
`Box2dRevoluteJointSpec`, configure them with `Box2dRevoluteMotor`, and remove them by stable
`Box2dJointId`. Apply copied body operations without exposing the private body handle:

```java
bridge.applyForceToCenter(torsoId, new Vec2(forceXNewtons, forceYNewtons));
bridge.applyTorque(torsoId, torqueNewtonMetres);
```

Force and torque are finite Box2D SI newtons and newton-metres, without render-unit conversion.
Call them only on the owner thread and cap their values in the application before calling the
bridge. The bridge resolves private body identity internally and rejects missing or inactive
bodies. Force accepts active dynamic or kinematic bodies and rejects static bodies; torque requires
an active dynamic body. Joint anchors use render units and pass through the declared unit
conversion; angular speed, motor torque, and angular limits use Box2D SI units/radians directly.
All operations are owner-thread checked and bounded by inspection limits. Controller intent still
enters only through ordered commands.

Install `bridge.contactListener()` or `bridge.composeContactListener(applicationListener)` on the
world. Do not replace it later without equivalent explicit composition. The evidence listener runs
first so application callback failure cannot erase inspection evidence.

Use one declared render-units-per-metre conversion everywhere. Collider sizes, positions, and joint
anchors enter Box2D through `toPhysicsUnits`; body positions and velocities return through
`toRenderUnits`. Joint inspection appears under `box2d.joint.<stable-id>`.

Close the gameplay world/bridge before disposing the native world. The bridge closes joint
inspection registrations and destroys its joints before unregistering and destroying endpoint
bodies; it never disposes the application-owned world. Reset uses the same joint-before-body
lifecycle boundary. Rebuild the native world when exact replay reset is required because Box2D
broadphase history is not authoritative reusable state.
