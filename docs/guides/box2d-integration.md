# Box2D integration

The application owns `com.badlogic.gdx.physics.box2d.World`, native initialization, and the one
installed contact listener. Create `GameplayBox2dBridge` on the world owner thread with one
`Box2dBodyFactory`, immutable `Box2dUnitConversion`, fixed `Box2dSolverSettings`, application-owned
runtime, and gameplay limits. Add the bridge as a lifecycle participant and add all three returned
systems.

The bridge creates and owns only bodies/fixtures corresponding to active gameplay entities. It
applies movement intent in `PRE_PHYSICS`, steps the native world in `PHYSICS`, and copies native
position/velocity authority back in `POST_PHYSICS`. Contact callbacks enqueue stable fixture
endpoints; gameplay consumes the bounded event list after the step.

Install `bridge.contactListener()` or `bridge.composeContactListener(applicationListener)` on the
world. Do not replace it later without equivalent explicit composition. The evidence listener runs
first so application callback failure cannot erase inspection evidence.

Use one declared render-units-per-metre conversion everywhere. Collider sizes and positions enter
Box2D through `toPhysicsUnits`; body positions and velocities return through `toRenderUnits`.
Close the gameplay world/bridge before disposing the native world, and unregister runtime
inspection before native destruction. Reset fixtures by rebuilding the native world when exact
replay reset is required; Box2D broadphase history is not authoritative reusable state.
