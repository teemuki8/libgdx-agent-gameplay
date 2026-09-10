# Optional Bullet 3D adapter (local candidate)

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
  Stance's canonical field order is grounded then crouched. Applications using strict prefabs
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
crouch clearance, step height and slope limit are configurable. Sliding and initial penetration
recovery each have six iterations. Unresolved starting overlap fails explicitly; do not hide it
with a teleport. Body count is 1..4096; dimensions/tick duration/coordinates are bounded before
native calls. This is a kinematic static-obstacle adapter, not dynamic rigid-body simulation,
network prediction, continuous moving-platform motion, or capsule-to-capsule combat collision.

## Snapshot rendering

`GameplayRenderer3D(batch, perspectiveCamera, resolver, maxEntries)` consumes completed snapshots.
The resolver maps application-owned logical entity/model information to an application-owned
`ModelInstance`, or null for invisible entities. It does not install a separate asset registry.
Call `resize(width,height)` after viewport changes and update camera pose from completed state.
Use `render(snapshot, environment)` for optional application-owned lighting. The application
clears color/depth buffers and chooses the physical GL viewport before rendering.

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
SHA-256 downloads before acceptance. No publication/release is performed for this local candidate.

Final scoped gate (2026-09-10): 9 Bullet tests and 11 gameplay-libgdx tests passed, including the
real framebuffer test on LWJGL 3.4.3. Both modules passed Checkstyle main/test and Javadocs under
`--warning-mode=fail`. LWJGL emits its upstream Java 25 `sun.misc.Unsafe` deprecation notice; no
Java compilation, Checkstyle, Javadoc or test failures remained. The independent core-package
review's allocation-order, scaled-bounds, renderer-overflow and copied-hit validation findings
were corrected before this gate.
