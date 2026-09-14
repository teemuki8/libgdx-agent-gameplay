# Optional Bullet 3D adapter (1.6.0)

`gameplay-bullet` is an additive optional module. Existing Box2D/2D modules and published
coordinates remain unchanged. The application creates and closes `GameplayBulletWorld(maxBodies)`;
its native collision objects, shapes, callbacks and Bullet infrastructure never escape the adapter.
All world, motor and renderer calls run on their construction thread. Native tests run in the
bootstrap's private PID namespace through `isolated-run.sh`; Linux GL tests additionally use Xvfb.
The native framebuffer test requests GLFW's X11 platform explicitly. This host's Wayland/NVIDIA EGL
path crashed in a contained test process; final verification passed with `WAYLAND_DISPLAY` removed,
`XDG_SESSION_TYPE=x11`, `LIBGL_ALWAYS_SOFTWARE=1` and `__GLX_VENDOR_LIBRARY_NAME=mesa`.

Units are metres, Y is up. Raw capsule queries take the centre and total height including both
hemispheres. `CharacterState.feet` and the bridged `Transform3D.position` are at the feet.
The motor's radius/height are configuration-owned, independent of presentation scale: author
character geometry to match the configured capsule. Pose rotation does not tilt the capsule.

## Dependencies

Align optional `io.github.teemuki8:gameplay-bullet:1.6.0` with the other gameplay modules.
Desktop applications explicitly supply `com.badlogicgames.gdx:gdx-platform:1.14.2:natives-desktop`
and `com.badlogicgames.gdx:gdx-bullet-platform:1.14.2:natives-desktop` as runtime-only dependencies.
The Bullet module POM contains no desktop backend or native classifier dependency.

## Ownership and integration

- Add level boxes with stable gameplay `EntityId`s and `addBox(id, pose, halfExtents)`. Quaternion
  rotation creates slopes; initial positive pose scale multiplies the half extents.
- `updateBoxTransform(id, pose)` moves/rotates an existing object and updates its broadphase AABB.
  It rejects scale changes; remove/add explicitly to resize. Perform updates in a stable gameplay
  system slot before authoritative ray queries. The app removes/reconstructs level objects at
  its own domain lifecycle/reset boundaries. The adapter never invents entity IDs.
- `raycast(from, to, maxHits)` copies bounded nearest-first hits, then sorts ties by stable ID.
  `sweepCapsule(from, to, radius, totalHeight)` copies one nearest blocking hit; tangencies and
  surfaces facing away from movement are ignored. No native pointer enters either result.
- Create `GameplayBulletBridge(world, config, intentResolver)` and register `physicsSystem()`
  in the existing `GameWorld`. It uses PHYSICS slot 10, so choose distinct slots for other systems.
- Register `CharacterStance.TYPE` with `CharacterStance.CODEC`, and add it alongside `Transform3D`
  and `Velocity3D` to capsule entities. These immutable components remain the sole character state.
  Stance's canonical field order is grounded, derived crouched, then crouchFraction. Applications using strict prefabs
  register an explicit local decoder for this optional component.
- Resolve `CharacterIntent` from commands processed by the application's production input system.
  Horizontal movement has length at most one. Jump is a press edge; crouch is held. Store previous
  jump state in an application-owned canonical component if converting held input to edges.
  The resolver must not poll `Gdx.input`, retain native state, or mutate the world out of phase.
- The bridge owns no native resources. Close the application game world first and then the
  application's Bullet world. Repeated `close()` is safe on the owning thread.

`BulletCharacterMotor.step` is also independently reusable from a PHYSICS system: pass immutable
state, command-derived intent and the authoritative fixed duration; write the returned state into
your existing components. It has no second state store. Acceleration/braking, gravity, jump,
crouch clearance, crouch top-speed ratio [0,1], step height and slope limit are configurable.
The twelve-argument constructor adds crouch transition seconds in [0,5], with zero selecting
instant transitions. Defaults use 0.18 seconds. The authoritative fraction drives capsule height,
movement speed and application eye height; growth pauses under obstructing ceilings.
The eleven-argument CharacterConfig constructor sets the ratio last and retains instant transitions; the ten-argument
compatibility constructor retains the original 0.5 ratio. The motor applies this ratio once. Sliding and initial penetration
recovery each have six iterations. Unresolved starting overlap fails explicitly; do not hide it
with a teleport. Body count is 1..4096; dimensions/tick duration/coordinates are bounded before
native calls. This is a kinematic static-obstacle adapter, not dynamic rigid-body simulation,
network prediction, continuous moving-platform motion, or capsule-to-capsule combat collision.

## Primitive rigid dynamics

`GameplayBulletDynamicsWorld` is a separate application-owned native world for bounded static
and dynamic primitive bodies. Its additive API does not change the existing
`GameplayBulletWorld` collision and character-motor contract. The application selects explicit
ceilings of 1..4096 bodies and 0..8192 constraints, supplies gravity, and advances exactly one
caller-owned fixed step at a time. No fixed player-count or game-rule ceiling is installed.

Each `BulletRigidBodySpec` supplies a stable `EntityId`, metre-scale BOX, Y-axis CAPSULE or SPHERE
bounds, unit-scale pose, mass, damping, surface values and collision bits. Initial linear and
angular velocity are copied during creation. `applyCentralImpulse` and `applyImpulse` accept
copied SI values; point impulses derive the relative centre offset inside the adapter.
`applyCentralForce` and `applyTorque` accept bounded SI values before the caller-owned fixed step,
wake the dynamic body, and affect that step only. The application owns controller tuning and
decides whether to repeat them; impulses remain the separate one-shot operation for impacts. A
`BulletSixDofConstraintSpec` locks an oriented anchor frame's translation and applies ordered
angular limits around its local axes between two stable body IDs. Its convenience constructor
uses world-aligned axes. X/Z stay in [-PI, PI], while Bullet requires Y in [-PI/2, PI/2]. The application supplies the stable
`BulletConstraintId`. Native bodies, shapes, motion states and constraints never cross the API.

`setCollisionIgnored(first, second, ignored)` changes one bounded pair of existing bodies.
Use it for selected self-contact exclusions while leaving collision with the environment enabled.
Removed bodies release their pair-filter entries.

Read state through copied `BulletRigidBodyState` pose and velocities after the fixed step. Remove
constraints before their bodies. `close()` does this ordering for all remaining resources and is
idempotent on the construction thread. All mutation, reads, steps and disposal are owner-thread
confined. This layer provides physical building blocks only: rig proportions, transition rules,
rendering, corpse lifetime, active motors, recovery and replication belong to the consuming game.

## Snapshot rendering

`GameplayRenderer3D(batch, camera, resolver, maxEntries)` consumes completed snapshots.
The resolver maps application-owned logical entity/model information to an application-owned
`ModelInstance`, or null for invisible entities. It does not install a separate asset registry.
Call `resize(width,height)` after viewport changes and update camera pose from completed state.
Use `render(snapshot, environment)` for optional application-owned lighting. The application
clears color/depth buffers and chooses the physical GL viewport before rendering.

Version 1.6.0 adds a common `Camera` constructor accepting an `OrthographicCamera`.
The published perspective constructor remains source- and binary-compatible. For orthographic
projection, initialize a positive finite vertical span in world units. For example, construct
`new OrthographicCamera(20, 12)` and call `renderer.resize(1600, 900)`: the vertical span remains
12 world units and the horizontal span becomes approximately 21.3333. The application's current
`zoom` still applies and is never reset. Invalid spans, zoom-scaled overflow and spans too small
to represent a finite projection scale fail before changing either
viewport dimension. Perspective cameras retain their previous pixel-dimension resize behavior.
The application owns camera pose, clipping planes, zoom and any screen-to-world conversion;
update these on the rendering thread.

The adapter bounds entries (1..4096), sorts stable IDs, frustum-culls transformed model bounds,
applies snapshot transforms only while preparing renderables, and restores model transforms.
It restores depth test/function/write-mask and cull enable/mode after ModelBatch finishes.
It never disposes the batch, camera, environment, models or materials. Close invalidates only
this renderer. Geometry/model complexity and environment light counts remain the application's
bounded asset contract; this adapter bounds entities, not arbitrary caller model internals.

## Local evidence

Native tests cover copied ray/sweep hits, deterministic tie ordering, body limits, moved boxes,
closed/thread access, floor landing/jump/wall blocking, clearance/starting overlap, low steps,
walkable slopes and steep slope rejection. A real GameWorld test verifies PHYSICS writes into
canonical pose, velocity and stance. A real LWJGL framebuffer test draws a near red box before a
far blue box and checks red depth occlusion plus retained caller depth/cull state. These library
checks do not qualify a game's production input, HUD/runtime correlation or visual direction.

Bullet 1.14.2 artifacts and LWJGL 3.4.3 test artifacts were resolved from Maven Central. Candidate
checksums from the repository refresh script were individually compared with direct Central
SHA-256 downloads before acceptance. Publication receipts are recorded in the versioned release notes.

Final scoped gate (2026-09-10): 9 Bullet tests and 11 gameplay-libgdx tests passed, including the
real framebuffer test on LWJGL 3.4.3. Both modules passed Checkstyle main/test and Javadocs under
`--warning-mode=fail`. LWJGL emits its upstream Java 25 `sun.misc.Unsafe` deprecation notice; no
Java compilation, Checkstyle, Javadoc or test failures remained. The independent core-package
review's allocation-order, scaled-bounds, renderer-overflow and copied-hit validation findings
were corrected before this gate.

Followup native gate: 10 Bullet tests passed, including full-speed versus quarter-speed crouch
displacement, with Checkstyle and Javadocs passing after making the crouch ratio configurable.

## Agent runtime inspection

Gameplay 1.6 uses the Runtime 3.2 Bullet adapter. Enable observation explicitly before runtime start:

```java
runtime.simulation().register(SimulationTimelineSpec.fixedStep(16_666_667));
dynamics.observe(runtime, "main", BulletContactLimits.developmentDefaults());
runtime.start();
// Create or remove bodies/joints between runtime capture frames, on the world owner thread.
runtime.simulation().tick(16_666_667, supplied -> {
    dynamics.step(supplied / 1e9);
    return supplied;
});
```

`observe` includes existing objects and registers future body/joint lifetimes automatically. Bodies,
shapes and joints use `bullet.body.main.<entityId>`, `bullet.shape.main.<entityId>` and
`bullet.joint.main.<constraintId>`. World and contact facts use `bullet.world.main` and
`bullet.contacts.main`. All values are copied; the public gameplay API never returns native handles.
An observed world requires one active simulation tick for every `step`; it rejects an uncaptured step
before physics advances. A world without observation retains its existing application-owned step API.

Positions and velocities are three-component objects; orientations are quaternions. Registered
6-DOF joints expose authored limits, copied current frames/angles/anchors and last-solver feedback.
This world owns and initializes the native joint-feedback allocation, then disposes it with the
constraint. Reaction vectors are therefore available to agents. The pinned libGDX binding does not
export linear-motor enabled arrays, so that one field is explicitly unavailable.

`BulletAssertions` and `BulletDeterminism` use the shared runtime control, input, assertion and replay
contracts. Sampled contact BEGIN/END transitions require complete post-step manifold evidence;
resource overflow and topology changes are explicit incomplete evidence until a complete baseline
recovers. `dynamics.contactTicks(fromTick, toTick, limit)` returns immutable contact history; the live
capture handle stays private. History is bounded and tied to completed native tick frames.

If `GameplayRuntimeBridge` already owns a frame, use Runtime's callback-owned frame contract instead
of nesting another frame. Topology edits still belong outside open capture frames. The ragdoll
bootstrap records the PHYSICS system's native step, before gameplay snapshot synchronization, and
captures its presentation values separately when rendering. Its existing harness stdio server exposes
registered native facts through `ui_runtime_observe`; it does not start a second MCP server.

`GameplayBulletInspectionTest` checks actual native stepping, dynamic lifetimes, contacts, joint
feedback, unavailable binding data and rejection of removal during an open capture frame.
