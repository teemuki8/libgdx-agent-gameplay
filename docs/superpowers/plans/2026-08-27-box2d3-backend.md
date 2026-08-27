# Box2D 3 Backend Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the teemuki8 legacy libGDX Box2D backend with the official Box2D 3.1.1 binding, genuine capsules, backend-neutral copied APIs, richer dynamics/query operations, and migrated runtime/gameplay/consumer evidence.

**Architecture:** `agent-runtime-box2d` 3.0 migrates first to Box2D 3 handles and publishes stable runtime inspection. `gameplay-box2d` 1.0 then consumes runtime 3.0, wraps an application-owned private `b2WorldId`, and implements bodies, genuine shapes, joints, contact impulses, dynamics, force-at-point, and bounded raycasts without exposing native IDs. Ragduel and bootstrap migrate only after both releases resolve publicly.

**Tech Stack:** Java 25, libGDX core 1.14.2, official `com.badlogicgames.gdx:gdx-box2d:3.1.1-0`, jnigen runtime 3.1.0, runtime 3.0.0, gameplay 1.0.0, Gradle wrappers, JUnit, Xvfb.

**Spec:** `docs/superpowers/specs/2026-08-27-box2d3-backend-design.md`

## Global Constraints

- No `com.badlogic.gdx.physics.box2d` dependency/import remains in runtime 3.0, gameplay 1.0, ragduel, or generated bootstrap projects.
- Native `b2WorldId`, `b2BodyId`, `b2ShapeId`, `b2JointId`, pointers, structs, closures, buffers, and callback data remain adapter-private; gameplay consumers receive copied immutable values only.
- Application owns one `GameplayBox2dWorld`; bridge closes before world; all operations remain owner-thread confined.
- Box2D 3 world worker count is zero and fixed substeps are bounded 1..16 (default 4) for the first deterministic backend.
- Public gameplay types are backend-neutral. No deprecated aliases or dual backend.
- CollisionImpact retains exact positive normal impulse semantics via bounded hit-event/contact-data correlation.
- Genuine native `b2Capsule` is required; no compound legacy emulation.
- Every collector/query/result/lifecycle is bounded and typed; overflow fails rather than truncates unless the public query explicitly reports truncation.
- Runtime 3.0 and gameplay 1.0 releases require separate explicit authorization after implementation, PR review, CI, compatibility-break documentation, archive/POM qualification, and unused namespace checks.
- Released consumers use Maven Central only; no Git/Maven Local/composite/snapshot fallback in qualification.
- Push branches and open PRs as requested; do not merge or publish without the task's explicit boundary authorization.

---

### Task 1: Prove the Official Box2D 3 Binding Vertically

**Repository:** `/home/tjaaskel/git/libgdx-agent-gameplay`

**Files:**
- Create: `gameplay-box2d/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2d3BindingSpikeTest.java`
- Modify temporarily then retain if accepted: `gradle/libs.versions.toml`
- Modify: `gameplay-box2d/build.gradle.kts`
- Modify generated: `gameplay-box2d/gradle.lockfile`, `gradle/verification-metadata.xml`

**Interfaces:**
- Consumes: official `gdx-box2d:3.1.1-0` low-level binding.
- Produces: grounded lifecycle/contact/capsule/impulse/raycast facts used by Tasks 2–8.

- [ ] Write a failing test that imports `com.badlogic.gdx.box2d.Box2d`, `b2WorldId`, `b2Capsule`, and `b2ContactEvents`; run before the dependency change and observe missing symbols.
- [ ] Add a distinct version catalog key `gdx-box2d3 = "3.1.1-0"`; point only gameplay-box2d spike configuration at the new binding and matching `gdx-box2d-platform` desktop natives. Do not globally replace legacy physics yet.
- [ ] Initialize with `Box2d.initialize()`, create/destroy a zero-worker world, dynamic body, genuine capsule shape, second body, revolute joint, and step with four substeps.
- [ ] Prove copied position, angle, angular velocity, mass/inertia, force, torque, force-at-point, raycast, begin/end/hit events, contact data, and maximum manifold normal impulse.
- [ ] Prove ID invalidation after shape/body/joint/world destruction and close every closure/owned struct according to jnigen ownership.
- [ ] Run the focused native test under Xvfb three times; require identical copied fact ordering/values within exact float serialization used by the planned API.
- [ ] Refresh strict verification twice and commit the spike/dependency evidence with `test: qualify official Box2D 3 binding`.

Expected command:

```bash
xvfb-run -a ./gradlew :gameplay-box2d:test --tests '*Box2d3BindingSpikeTest' --warning-mode=fail
```

---

### Task 2: Migrate Runtime Box2D Inspection to Box2D 3

**Repository:** `/home/tjaaskel/git/libgdx-agent-runtime`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `runtime-box2d/build.gradle.kts`
- Replace internals/public registrations: `runtime-box2d/src/main/java/io/github/teemuki8/libgdx/agent/runtime/box2d/Box2dInspection.java`
- Modify value declarations under `runtime-box2d/src/main/java/io/github/teemuki8/libgdx/agent/runtime/box2d/`
- Replace native tests under `runtime-box2d/src/test/java/`
- Update: `docs/guides/box2d-inspection.md`, `docs/guides/agent-cookbook.md`, README, changelog/migration notes
- Update generated locks/verification metadata/publication contracts

**Interfaces:**
- Consumes: Task 1 handle/lifecycle facts.
- Produces: runtime 3.0 `registerWorld/body/shape/joint` APIs accepting Box2D 3 IDs and preserving stable runtime entity IDs.

- [ ] Create an isolated runtime worktree; preserve existing untracked IDE files in the main checkout.
- [ ] Write RED API/native tests using `b2WorldId`, `b2BodyId`, `b2ShapeId`, and `b2JointId`, including a capsule shape and stale-ID failures.
- [ ] Replace legacy dependencies/imports with `gdx-box2d:3.1.1-0`; use backend-native structs only inside runtime-box2d.
- [ ] Implement private scalar keys `(index1, world0, generation)` for weak/liveness tracking; never serialize handle indices.
- [ ] Preserve stable runtime IDs `box2d.world.*`, `body.*`, `fixture.*`, `joint.*` and compatible properties; add angular velocity, mass, inertia, capsule endpoints/radius, and Box2D 3 counters.
- [ ] Close registrations before native destruction; reject invalid/stale/wrong-world/duplicate/bound/owner-thread/closed calls with typed evidence.
- [ ] Remove all legacy imports and locks from runtime; add an architecture test scanning published runtime source/dependencies.
- [ ] Update cookbook and migration guide with complete Box2D 3 construction/registration/close recipe.
- [ ] Run `.agents/skills/libgdx-agent-runtime-dev/scripts/verify.sh full`, Javadocs, API break report against 2.2.0, archives/POMs and strict metadata idempotence.
- [ ] Commit in focused red/green units, request review, push branch and open a runtime 3.0 PR. Stop before merge/release authorization.

---

### Task 3: Release Runtime 3.0.0

**Repository:** `/home/tjaaskel/git/libgdx-agent-runtime`

**Interfaces:**
- Consumes: reviewed green runtime PR and explicit merge/publication authorization.
- Produces: public runtime 3.0.0 artifacts for Task 4.

- [ ] Merge only after required CI/review and explicit shared-branch authorization.
- [ ] Confirm tag/coordinates 3.0.0 unused.
- [ ] Create exact release/tag and stage signed artifacts only after explicit publication authorization.
- [ ] Inspect candidate state, corrected metadata, exact expected PURLs and archive contents; publish exact validated UUID only.
- [ ] Wait for every runtime 3.0.0 POM/JAR to resolve publicly before Task 4.

---

### Task 4: Introduce Backend-Neutral Gameplay Public Types

**Repository:** `/home/tjaaskel/git/libgdx-agent-gameplay`

**Files:**
- Create: `Box2dBodyType.java`, `Box2dWorldSpec.java`, `GameplayBox2dWorld.java`, `Box2dBodySpec.java`, `Box2dBodySpecResolver.java`, `Box2dRaycastSpec.java`, `Box2dRaycastHit.java`
- Replace: `Box2dBodyState.java`, `Box2dBodyFactory.java`, `Box2dSolverSettings.java`
- Modify: `GameplayBox2dBridge.java` constructor/world APIs
- Modify all public API tests/Javadocs/docs

**Interfaces:**
- Consumes: public runtime 3.0 and official Box2D 3 binding.
- Produces: backend-neutral gameplay 1.0 public API and application-owned opaque world wrapper for later backend tasks.

- [ ] Pin runtime 3.0 and Box2D 3 Central artifacts with strict idempotent metadata refresh.
- [ ] Write RED API tests proving no legacy type appears in any exported signature.
- [ ] Add exact validated public values from the spec and replace `BodyDef.BodyType` with `Box2dBodyType`.
- [ ] Implement owner-thread `GameplayBox2dWorld.create/close`, private `b2WorldId`, zero workers, bounded substeps and application ownership.
- [ ] Replace body type resolver with full copied `Box2dBodySpecResolver`.
- [ ] Expand copied `Box2dBodyState` with angle/angular velocity/mass/inertia.
- [ ] Remove every `com.badlogic.gdx.physics.box2d` public/internal import from migrated production files.
- [ ] Run compile/Javadoc/API surface tests and commit `feat: introduce backend-neutral Box2D API`.

---

### Task 5: Port Bodies, Genuine Shapes, Materials, and Dynamics

**Files:**
- Modify: `Box2dBodyFactory.java`, private body/shape handles, unit conversion, bridge activation/disposal
- Modify: standard Collider schema in gameplay-core and canonical codecs/tests
- Modify runtime inspection wiring and real-native bridge tests

**Interfaces:**
- Consumes: Task 4 types/world.
- Produces: static/kinematic/dynamic Box2D 3 bodies and genuine BOX/CIRCLE/CAPSULE shapes with copied dynamics.

- [ ] Write RED tests for vertical/horizontal genuine capsules, invalid equal/undersized dimensions, body specs and copied dynamics.
- [ ] Add `Collider.Shape.CAPSULE` cleanly to core schema/canonical/prefab parsing.
- [ ] Build b2BodyDef/b2ShapeDef from validated specs; create genuine `b2Capsule` with major-axis endpoints/radius.
- [ ] Map scalar body/shape keys privately and register runtime body/shape evidence.
- [ ] Copy Transform2D/Movement/body dynamics after each completed step.
- [ ] Destroy shapes/body and registrations in strict order; prove stale IDs invalid and application world survives bridge close.
- [ ] Run core canonical, prefab, runtime and full real-native body tests; commit `feat: port gameplay bodies to Box2D 3`.

---

### Task 6: Port Joints and Physical Operations

**Files:**
- Modify revolute types/bridge internals/tests
- Add force-at-point implementation/tests

- [ ] Write RED tests for Box2D 3 revolute limits, motors, state, reset/disposal and force/torque/point-force.
- [ ] Port stable joint maps to private scalar b2JointId keys and b2RevoluteJointDef.
- [ ] Preserve copied APIs and exact owner/open/locked/active/body-type diagnostics.
- [ ] Implement `applyForce(EntityId, Vec2, Vec2 worldPointRenderUnits)` with SI force and converted point.
- [ ] Prove joint-before-body destruction, runtime evidence, stale ID rejection and no native identity.
- [ ] Commit `feat: port gameplay joints and forces to Box2D 3` after focused/native/Javadoc gates.

---

### Task 7: Port Contact Events and Preserve Normal Impulses

**Files:**
- Replace: `Box2dContactCollector.java` and tests
- Modify bridge PHYSICS/POST_PHYSICS flow, fixture identity maps, gameplay/runtime event tests

- [ ] Write RED real-native tests for begin/end/hit arrays and exact `CollisionImpact.normalImpulse`.
- [ ] After each step copy event shape IDs immediately; query bounded contact data; match the pair; copy maximum positive manifold normal impulse.
- [ ] Preserve stable endpoint normalization and STARTED/IMPACT/ENDED deterministic ordering.
- [ ] Preallocate bounded collectors/contact buffers; overflow typed, never truncate.
- [ ] Cover speculative hits, missing pair/impulse, multiple manifold points, capacity exhaustion, reset/close and no pointer retention.
- [ ] Preserve canonical/runtime collision event contracts unchanged.
- [ ] Commit `feat: port collision evidence to Box2D 3` after native/runtime/core gates.

---

### Task 8: Add Bounded Copied Raycast

**Files:**
- Implement bridge `raycast(Box2dRaycastSpec)`
- Tests/docs/cookbook examples

- [ ] Write RED tests for order, filtering, unknown shapes, maxHits 1..64, zero/multiple hits, callback cleanup, wrong thread/closed world.
- [ ] Implement one call-scoped bounded closure collector around b2World_CastRay; copy point/normal/fraction and stable identities immediately.
- [ ] Sort immutable hits by fraction then fixture ID; release closure/buffers in every path.
- [ ] Run native/Javadoc tests and commit `feat: add bounded copied Box2D raycast`.

---

### Task 9: Qualify and Push Gameplay 1.0 PR

- [ ] Port gameplay fixture, deterministic replay, runtime evidence and all documentation/migration recipes.
- [ ] Assert no legacy imports/dependencies/locks and exact new native artifacts.
- [ ] Run core, libgdx, runtime, box2d, fixture, Javadocs, clean check, API break report against 0.3.0, archives/POMs and metadata idempotence.
- [ ] Review whole branch for handle lifetime, closure cleanup, event semantics, deterministic ordering and public API consistency.
- [ ] Push and open focused gameplay 1.0 PR; stop before merge/release authorization.

---

### Task 10: Release Gameplay 1.0.0

- [ ] Merge only after green CI/review and explicit authorization.
- [ ] Confirm 1.0.0 tag/coordinates unused.
- [ ] Stage, inspect and publish exact validated candidate only after separate publication authorization.
- [ ] Wait for every gameplay 1.0.0 POM/JAR before consumer migration.

---

### Task 11: Migrate Ragduel to Box2D 3

**Repository:** `/home/tjaaskel/git/ragduel`

- [ ] Create clean worktree from current authoritative branch; preserve provisional hybrid patch separately.
- [ ] Pin runtime 3.0/gameplay 1.0/Box2D 3 Central artifacts; refresh strict metadata twice.
- [ ] Remove temporary gameplay composite and old Box2D natives/dependencies.
- [ ] Replace world creation with application-owned `GameplayBox2dWorld`; use genuine capsules for torso/hips/limbs/palms/feet.
- [ ] Apply provisional reviewed anatomy/balance/palm/UI changes, resolving against new backend without source copy fallback.
- [ ] Rerun exact idle/tilt/180-recovery/KO, palm causation, deterministic replay, MCP invariant, screenshots/layout/runtime and full clean gates.
- [ ] Replace provisional evidence once from retained Central-backed runs; review and push ragduel PR.

---

### Task 12: Update Bootstrap and New-Project Guardrails

**Repository:** `/home/tjaaskel/git/libgdx-agent-bootstrap`

- [ ] Pin runtime 3.0/gameplay 1.0/Box2D 3 and strict verification artifacts.
- [ ] Update generated starter world/body/capsule APIs and architecture guards.
- [ ] Add physics capability matrix to first-playable template: required shapes/joints/dynamics/queries/automation before implementation.
- [ ] Add mandatory original-size visual/joint checkpoint before balance/combat tuning.
- [ ] Separate deterministic mechanics, MCP invariants and visual evidence columns.
- [ ] Add three-hypothesis tuning stop rule and final-evidence-once-after-dependency-qualification rule.
- [ ] Run generated-project refresh/idempotence, smoke/MCP and full clean checks; push bootstrap PR.

---

### Task 13: Follow-up Harness PRs

**Repository:** `/home/tjaaskel/git/libgdx-ui-harness`

These are independent from Box2D migration and use separate specs/PRs:

- [ ] Gesture schema v2: one atomic exact-tick request up to 256 bounded steps; preserve v1/64.
- [ ] `ui_runtime_observe`: correlated bounded entity/property observation without an Actor binding.
- [ ] Opaque artifact chunk read/export by receipt with session quota/expiry and no arbitrary paths.
- [ ] Run core/protocol/MCP/real LWJGL fixture, docs/ADR, compatibility and full gates; push separate PRs, no publication without authorization.
