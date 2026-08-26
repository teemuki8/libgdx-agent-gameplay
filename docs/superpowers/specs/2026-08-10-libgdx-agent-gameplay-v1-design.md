# libGDX Agent Gameplay V1 Design

Date: 2026-08-10
Status: approved for specification
Repository: `teemuki8/libgdx-agent-gameplay` (public)

## Purpose

`libgdx-agent-gameplay` is the opinionated gameplay-construction layer between raw libGDX and
`libgdx-agent-runtime`. It constrains ordinary 2D gameplay into one small ECS-like model so coding
agents do not invent a new manager graph, update loop, physics authority, rendering authority, or
test-only control path for every game.

The primary optimization target is predictable composition and deterministic execution. V1 does
not target archetype-level ECS throughput. Its strongest success measure is how little
architectural freedom an agent must exercise to implement an ordinary mechanic correctly.

## Decisions and delivery boundary

- The repository is public and uses group `io.github.teemuki8`.
- The implementation is a purpose-built ECS-lite rather than an Ashley facade or a general plugin
  kernel.
- V1 uses closed, bounded JSON prefab documents.
- The canonical qualification is an original top-down arena game.
- The repository, CI, Maven publications, and manual release workflows will be created and pushed.
- This task does not authorize a version tag, GitHub release, Maven Central staging operation, or
  Maven Central publication. Those actions require separate release authorization.
- The current dependency baseline is JDK 25, Gradle wrapper 9.7.0, libGDX 1.14.2,
  `libgdx-agent-runtime` 2.2.0, `libgdx-ui-harness` 1.2.1, and `libgdx-ui-markup` 0.5.0.

## Alternatives considered

### Purpose-built ECS-lite (selected)

The library owns a type-keyed component store, immutable component values, an explicit system
schedule, staged lifecycle mutations, typed commands/events, strict prefab parsing, and narrow
libGDX/runtime/Box2D adapters. This creates the smallest vocabulary and makes misuse observable.

### Ashley facade (rejected)

Ashley would reduce storage implementation work but expose a second public vocabulary, preserve
easy escape routes around the intended schedule and lifecycle, and make deterministic/runtime
contracts wrapper-dependent.

### Ports-and-plugins kernel (rejected)

A replaceable storage/scheduler/parser/rendering kernel would maximize flexibility while recreating
the architectural choice burden this project exists to remove.

## Repository and module architecture

The repository contains four published Java-library modules and one non-published application:

```text
gameplay-core
├── gameplay-libgdx
├── gameplay-runtime
└── gameplay-box2d

gameplay-fixture -> all four modules + markup + harness
```

Published Maven artifacts are:

```text
io.github.teemuki8:gameplay-core
io.github.teemuki8:gameplay-libgdx
io.github.teemuki8:gameplay-runtime
io.github.teemuki8:gameplay-box2d
```

`gameplay-core` is GL-free. It owns gameplay data, fixed-tick execution, lifecycle, prefab parsing,
commands, events, snapshots, GL-free visual-evidence records, limits, and diagnostics. It uses the
bounded Jackson Core 2.22.1 streaming parser without databind polymorphism or default typing. Core
must not depend on libGDX, Box2D, Scene2D, the harness, or agent runtime.

`gameplay-libgdx` depends on core and libGDX. It owns the fixed-step application-loop adapter,
logical asset resolution, animation presentation, rendering, camera projection, and immutable
production of core's world-visual snapshot model.

`gameplay-runtime` depends on core and `agent-runtime-core:2.2.0`. It registers gameplay entities,
properties, events, change causes, simulation metadata, and visual evidence with an
application-owned `AgentRuntime`.

`gameplay-box2d` depends on core, libGDX Box2D, and `agent-runtime-box2d:2.2.0`. It owns the mapping
between gameplay entities and bridge-created native bodies/fixtures. It does not own the Box2D
world.

`gameplay-fixture` is not published. It is the executable reference architecture and the canonical
example intended for later adoption by `libgdx-agent-bootstrap`.

Dependency direction is one-way. No existing runtime, markup, harness, or libGDX artifact depends
on this repository.

## Core data model

### Identities

`EntityId`, `PrefabId`, `ComponentType<T>`, `SystemId`, and command-source IDs are explicit bounded
value objects. Entity IDs are semantic strings suitable for runtime evidence. The public model
never exposes object identity, array positions, Box2D addresses, Actor identity, or snapshot-local
node IDs as durable identity.

Entity iteration, snapshot serialization, diagnostics, and runtime projection use stable ID order.
A deterministic allocator produces names such as `projectile-0001` from an explicit prefix and
resettable counter. Callers may supply explicit IDs when a design requires a stable semantic name
such as `player` or `enemy-primary`.

### Components

V1 provides immutable, validated component records for the standard path:

- `Transform2D`: position, rotation, size, and normalized pivot;
- `Movement`: intended velocity and maximum speed;
- `Health`: current and maximum health;
- `Faction`: stable faction value;
- `Lifetime`: remaining simulation ticks;
- `Collider`: shape, dimensions, offset, sensor state, and filter values;
- `Sprite`: logical asset and region IDs, visual size, and origin;
- `Animation`: declared clips and current clip/frame state;
- `Render`: layer, explicit order, tint, and visibility.

Components contain data only. They cannot retain `Actor`, `Stage`, `Body`, `Fixture`, `World`,
`Texture`, `TextureRegion`, `SpriteBatch`, camera, runtime registrations, or disposal callbacks.

Applications may add a custom component only by registering a stable `ComponentType<T>` and an
explicit core codec. Runtime exposure additionally requires an explicit `RuntimeProjection<T>`
registered through `gameplay-runtime`. There is no reflection, annotation scanning, class-name
input, arbitrary object traversal, or default serialization.

### Entities and mutation

`GameWorld` is mutable only on the thread that created it. Other threads may consume completed,
deeply immutable, bounded snapshots.

Public entity access is read-only through `EntityView`. Systems receive a phase-scoped
`SystemContext` that permits component replacement and queues structural changes. An entity's
component set cannot change while a query is being iterated. Systems never retain an `EntityView`
or mutable editor beyond the callback in which it was supplied.

The library favors clear map-based storage over archetypes, sparse sets, pooling, or generated
accessors. Performance work requires measurements and a compatible internal replacement; it does
not change the public component or scheduling model in V1.

## Deterministic execution

### Fixed tick

Core advances exactly one explicit simulation tick at a time. A tick carries an integer tick ID
and a fixed nanosecond duration. The libGDX adapter runs a bounded accumulator at 60 ticks per
second for the fixture, clamps an oversized render delta, and executes at most five catch-up ticks
per rendered frame. Qualification can advance exact ticks without wall-clock timing.

### System phases and slots

The stable phase catalog is:

```text
INPUT
PRE_PHYSICS
PHYSICS
POST_PHYSICS
GAMEPLAY
ANIMATION
RENDER_PREP
RUNTIME_CAPTURE
```

Every `GameSystem` declares a `SystemId`, one phase, and a numeric slot. Schedule compilation:

- rejects duplicate system IDs;
- rejects duplicate phase/slot pairs;
- validates the bounded system count and slot range;
- sorts by phase and slot;
- produces an immutable, inspectable schedule before the first tick.

Registration order is never an execution-order tie-breaker. The world cannot add, remove, or
reorder systems after schedule compilation.

### Commands

Player-, AI-, replay-, and future network-relevant intent enters through typed commands such as
`MoveCommand`, `AimCommand`, `FireCommand`, `InteractCommand`, and `UseAbilityCommand`.

Each `CommandEnvelope` contains a target tick, stable source ID, monotonic source sequence, and
immutable typed payload. Commands for a tick are ordered by source ID and source sequence.
Duplicate source sequences, commands outside the retained future window, and late commands fail
with typed diagnostics. Systems do not poll keyboards or controllers directly.

The fixture's input processor converts production WASD and Space input into command envelopes. A
tap is guaranteed at least one simulation tick of intent; a held key continues until its genuine
key-up event. This lets harness 1.2.1's key-down/key-up `Press` exercise the same production path
without a gameplay toggle or test-only controller.

### Events

`GameplayEvent` is a typed immutable event with tick ID, tick-local sequence, subject/source
identity where applicable, and bounded attributes. Standard events include entity spawn/despawn,
damage, kill, item collection, projectile creation, and objective completion.

Events are appended only during an open tick, read in stable sequence, retained under hard limits,
and copied into completed snapshots. They do not mutate state by themselves; systems perform state
changes and record explicit event/change attribution.

## Entity lifecycle and reset

Lifecycle is explicit:

```text
EntityDraft
-> queued spawn
-> start-of-tick activation barrier
-> active fixed-tick updates
-> queued despawn
-> post-gameplay logical removal barrier
-> post-runtime-capture native disposal barrier
```

Prefab instantiation creates an `EntityDraft` detached from the world. `spawn` validates identity
and components, then queues activation. Spawned entities become visible at the documented
start-of-tick barrier, where adapters create and register native mappings before the runtime frame
opens. Despawn requests are idempotent within a tick. After `GAMEPLAY`, the logical removal barrier
makes those entities inactive, omits them from render preparation and gameplay runtime projection,
and emits their despawn events while retaining native mappings long enough for final evidence.
After `RUNTIME_CAPTURE` closes the runtime frame, the native disposal barrier unregisters runtime
handles and destroys bridge-owned native objects. Events describe both transitions.

Core components own no native resources. Adapter lifecycle participants receive deterministic
spawn/despawn/reset callbacks and release only the native resources they created. Disposal runs
from leaves to roots and in reverse registration order within a dependency level.

Reset is a requested world operation executed only at a tick boundary after the native disposal
barrier. It clears command/event and lifecycle queues, removes active entities, invokes adapter
cleanup, resets tick and semantic-ID state, and invokes one precompiled `WorldInitializer` that
spawns the declared initial prefabs. Reset cannot race an open system callback or runtime frame.

## Prefab contract

V1 chooses JSON because it avoids YAML aliases and implicit scalar typing, is reliable for agent
generation, supports JSON Schema, and can be parsed with closed bounded semantics.

A document uses this shape:

```json
{
  "schemaVersion": "gameplay-prefabs/1",
  "prefabs": [
    {
      "id": "goblin",
      "components": [
        {"type": "transform", "size": [0.8, 1.1], "pivot": [0.5, 0.5]},
        {"type": "movement", "maxSpeed": 3.0},
        {"type": "health", "current": 30, "max": 30},
        {"type": "faction", "value": "enemy"},
        {"type": "sprite", "asset": "enemies/goblin"},
        {"type": "collider", "shape": "box", "size": [0.5, 0.9]}
      ]
    }
  ]
}
```

Arrays preserve author intent and allow duplicate prefab and component IDs to be diagnosed. The
parser rejects duplicate JSON keys, unknown fields, trailing tokens, unsupported schema versions,
unknown component types, incompatible component combinations, invalid enums, non-finite numbers,
and values outside component bounds.

Default limits are:

- 1 MiB encoded document;
- JSON nesting depth 32;
- 1,024 prefabs per document;
- 64 components per prefab;
- 256 characters per identifier or ordinary string;
- 256 values in any other JSON array;
- 128 characters in a numeric token;
- 64 validation diagnostics per document;
- 512 characters in each corrective example.

V1 has no inheritance, expressions, scripts, includes, URL loading, caller-selected filesystem
paths, arbitrary spawn-time JSON patches, or general level language. A spawn supplies a prefab ID,
semantic entity ID, and optional initial pose. Full declarative levels remain a later module, but
the stable prefab/entity IDs and spawn API are designed to support future entities, spawn points,
collision, triggers, objectives, zones, camera bounds, navigation hints, and encounters without
changing core authority.

Logical asset values are identifiers, never paths. The application-owned asset resolver decides
which bundled asset corresponds to an identifier.

## Diagnostics and bounds

Trust-boundary failures use a closed `GameplayDiagnosticCode` catalog and immutable
`GameplayDiagnostic` evidence. A diagnostic carries:

- stable code and retryability;
- operation and lifecycle state;
- tick, entity, prefab, component, command, or system identity when relevant;
- JSON pointer and source location for prefab failures;
- expected bound/type and observed bounded value;
- a bounded corrective example;
- a deterministic correlation ID where the operation crosses into runtime evidence.

Messages are explanatory but callers branch on codes. Remote/public evidence contains no stack
trace, native address, secret, credential, arbitrary path, or raw unbounded application message.
Limits apply to entities, components, systems, queued commands/mutations, events, snapshots,
strings, prefab input, projections, visual entries, and diagnostic collections. Exhausting a limit
never silently truncates authoritative state into an apparently successful result.

The V1 world defaults allow at most 10,000 active entities, 64 components per entity, 256 systems,
4,096 future commands, 4,096 pending lifecycle mutations, 4,096 events per tick, 10,000 visual
entries, and 4 MiB of canonical completed-snapshot data. Applications may lower these values but
cannot exceed the library maxima in V1.

## Box2D integration and authority

The application constructs, steps, and disposes the Box2D `World`. `GameplayBox2dBridge` is created
on the simulation/render thread and receives that application-owned world plus one immutable unit
conversion and explicit solver settings.

The bridge creates and indexes only bodies and fixtures declared by gameplay entities. Its private
maps associate stable `EntityId` values with native objects; native objects never enter the
component model.

Authority is fixed:

- `Transform2D` provides a physics-backed entity's spawn pose.
- After activation, a dynamic Box2D body owns translation, rotation, velocity, and collision
  response.
- `PRE_PHYSICS` converts movement/ability intent into bounded body operations.
- The application-owned `World.step` occupies the `PHYSICS` slot.
- `POST_PHYSICS` copies body pose and velocity into gameplay components exactly once.
- Static/kinematic policies are declared at body creation and cannot switch authority implicitly.
- Visual size/pivot and collider size/offset may differ only through explicit component values.
- One unit conversion is used by body/fixture creation, rendering, runtime inspection, and
  alignment evidence.

Contact callbacks copy bounded facts immediately. After the step, stable entity endpoints produce
typed gameplay events; systems never retain a Box2D `Contact` or other callback-owned object.

The bridge creates an explicitly owned `Box2dInspection` from `agent-runtime-box2d:2.2.0` and
registers its world, bodies, fixtures, and selected contact evidence using stable derived IDs.
Registration and unregistration occur only at the documented barriers while no runtime frame is
open. Closing the bridge unregisters runtime handles and destroys bridge-created fixtures/bodies
in reverse dependency order. It never disposes the application-owned world or replaces an
application contact listener without explicit composition.

## Rendering, assets, and animation

`GameplayRenderer` consumes completed authoritative gameplay state. It receives an
application-owned `OrthographicCamera`, `SpriteBatch`, and `AssetResolver`; it does not dispose
them. The resolver maps logical asset/region IDs to already-owned libGDX resources and reports a
typed missing-asset diagnostic.

Animation definitions declare named clips, ordered logical frames, positive frame duration in
simulation ticks, and loop mode. Animation state advances in `ANIMATION`; rendering never advances
gameplay time. Frame selection, origin, visual size, tint, visibility, and filtering policy are
therefore predictable inputs rather than ad-hoc Actor/Sprite behavior.

Draw order is stable by render layer, explicit order, and `EntityId`. Rendering may interpolate
presentation between completed poses, but interpolated values never flow back into gameplay,
physics, commands, events, runtime authority, or deterministic snapshot digests.

Asset ownership is explicit: the application owns loaded textures, atlases, batch, and camera;
the renderer owns only its internal bounded caches and clears them on close.

## World visual semantics

After `RENDER_PREP`, the libGDX adapter produces a bounded immutable `WorldVisualSnapshot` for the
completed simulation tick and current camera/framebuffer mapping. Each important entity can expose:

```text
entityId
worldPosition
spriteBounds
screenBounds
pivot
rotation
visible
cameraVisible
renderLayer
renderOrder
colliderBounds
unitConversion
alignmentDelta
```

Screen bounds use a documented top-left framebuffer coordinate system; gameplay/Box2D world
coordinates retain their documented world orientation. Bounds, origins, transformations, and
unit scales are copied values, not live native references.

`cameraVisible` is derived from the declared camera and sprite bounds. `alignmentDelta` compares
declared visual and collider centers after applying their explicit pivots, offsets, rotations, and
unit conversion. Missing evidence is typed as unavailable rather than guessed.

This is semantic inspection from the game's own render model. V1 does not perform unrestricted
image recognition. Original-resolution screenshots remain separate supplementary evidence.

## Agent-runtime integration

`GameplayRuntimeBridge` is explicitly installed with an application-owned `AgentRuntime` and a
bounded projection registry before simulation begins. It registers one dynamic entity source whose
values come from completed gameplay snapshots, not render widgets or native physics objects.

Standard projections include:

```text
transform.position / rotation / size / pivot
movement.velocity / maxSpeed
health.current / max / alive
faction.value
lifecycle.state
visual.worldPosition / spriteBounds / screenBounds
visual.visible / cameraVisible / renderLayer / renderOrder
physics.colliderBounds / unitConversion / alignmentDelta
```

Standard gameplay events map to runtime events with subject/source attribution and bounded
attributes. When a gameplay event caused a component change, the bridge records explicit change
causality rather than inferring it from adjacent frames.

After the activation barrier, the bridge opens the corresponding agent-runtime frame before
`INPUT`. It maps explicitly attributed events and changes while that frame is open. `RENDER_PREP`
then creates the current tick's immutable world-visual snapshot. The declared `RUNTIME_CAPTURE`
phase completes the runtime frame after authoritative gameplay, physics, animation, and visual
preparation. Only then may the native disposal barrier mutate Box2D/runtime registrations. The
application records the simulation/runtime/UI frame correlation required by runtime and harness.
A missing or stale proof remains unavailable or stale; the bridge never guesses correlation.

Markup HUD elements declare matching `data-runtime-entity` and `data-runtime-property` values.
`MarkupRuntimeSource.registerBindings` installs only UI correlations because gameplay runtime
registrations remain the independent authority. A deliberate HUD/domain divergence must therefore
produce `MISMATCH` through harness `ui_runtime_compare`.

The runtime remains architecture-neutral: all dependencies point from gameplay integration toward
the existing runtime artifacts.

## Qualification fixture

The fixture is an original 960 by 540 top-down arena. Its repeatable 30-second loop is:

1. Start through the markup title control.
2. Move the player with WASD inside the arena walls.
3. Fire with Space at one pursuing enemy.
4. Projectiles collide through Box2D and apply one damage each.
5. Three hits trigger damage feedback, death animation, enemy despawn, and score reward.
6. Reset through the markup HUD control and repeat from the same seed.

The fixture contains player, enemy, projectile, and wall prefabs. It demonstrates movement, enemy
behavior, attack, collision, damage, death, HUD, reset, assets, animation, stable render order,
runtime projections, Box2D evidence, and world visual semantics. The HUD is built only from
`hud.xml` and `hud.gdxcss` through `MarkupBuilder` and `HarnessSemanticSink`. There is one
application-owned Stage, input multiplexer, render loop, harness session, and runtime.

The safe gameplay region excludes the persistent HUD strip. The design records player/enemy visual
dimensions, origins, pivots, collider sizes/offsets, the world-to-Box2D unit conversion, and the
camera mapping before implementation.

The repository includes a completed `docs/evidence/first-playable.md` covering mechanic intent,
authored versus simulated degrees of freedom, production input, fixed-step boundary,
visual/collision alignment, safe region, persistent HUD, screenshots, layout findings, runtime
comparison, walkthrough observations, and remaining subjective feel risks.

## Qualification evidence and tests

Every behavior change follows red-green-refactor. Production code begins only after a focused test
has failed for the expected missing behavior. Tests call public behavior and real input paths;
they do not invoke UI listeners or system internals directly.

The verification ladder is:

1. GL-free core unit tests for IDs, components, schedule compilation, lifecycle barriers,
   commands, events, prefab parsing, bounds, and diagnostics.
2. libGDX and Box2D adapter tests on the render thread under Xvfb.
3. Deterministic fixture reruns from the same reset seed and command transcript, comparing every
   canonical completed-tick snapshot and gameplay-event digest.
4. Real LWJGL3 startup smoke under Xvfb.
5. Real harness MCP E2E: session discovery, semantic query, production action, wait, assertion,
   screenshot, and cleanup.
6. Correlated `ui_runtime_compare` equality for HUD values and deliberate mismatch coverage.
7. Runtime assertions for health, contact-attributed damage, death, score, and removal.
8. Broad `ui_validate_layout` checks in the actionable gameplay state. A
   `CHECK_UNAVAILABLE` finding is not treated as a pass.
9. Original-resolution screenshots of the first actionable frame, movement/fire response, and
   enemy death/reset, each opened and visually inspected.
10. Maven-local publication/consumer checks, POM validation, archive license checks, signatures,
    dependency locks, verification metadata, Javadocs, and API compatibility.
11. Full Linux gate:

    ```bash
    xvfb-run -a ./gradlew clean check javadoc --warning-mode=fail
    ```

The title/startup smoke alone is never accepted as gameplay evidence. Automated gates cover
objective contract failures, not subjective fun, balance, art quality, or feel thresholds.

## Build, compatibility, CI, and release preparation

Gradle uses `FAIL_ON_PROJECT_REPOS`, Maven Central only, dependency locking on all configurations,
strict dependency verification, reproducible archives, sources/Javadoc JARs, Apache-2.0 license
notices, warning-free Java/Javadoc, Checkstyle, JaCoCo, and published-module API compatibility.

After wrapper, plugin, or dependency changes, the repository runs its bounded verification
metadata refresh script, reviews the exact metadata diff, and commits only trusted additions.
Ordinary builds and IDE synchronization never auto-trust artifacts.

GitHub Actions are pinned to immutable revisions. CI runs focused core tests, Xvfb adapter/fixture
tests, the full gate, dependency/archive checks, API compatibility, and Maven-local consumer
verification. Artifacts retained on failure are bounded and contain no credentials.

Manual release workflows adapt the validated sibling-library logic: validate an exact version tag,
build and sign every publication, stage to a user-managed Central deployment, verify exact
coordinates and corrected POM/archive metadata, and expose separate inspect/publish/drop actions.
They use the protected `maven-central` environment and repository secrets.

No release workflow is triggered during this task. Completion for this task means a clean verified
public `main` branch with local/remote head parity and GitHub repository state confirmed. It does
not mean Maven Central publication.

## Implementation decomposition

This repository-level design is implemented through sequential, independently reviewable
milestones rather than one undifferentiated patch:

1. Repository/build foundation plus the deterministic core lifecycle and schedule.
2. Standard components, commands/events, strict prefab parsing, and deterministic transcript
   evidence.
3. libGDX rendering/visual semantics, runtime projection, and Box2D ownership adapters.
4. The canonical arena fixture, markup/harness/runtime wiring, and first-playable evidence.
5. Maven-local publication qualification, API compatibility, CI/release workflows, documentation,
   public GitHub publication, and final remote/CI audit.

Each milestone begins with failing behavioral tests, leaves all earlier gates green, and receives
its own review before the next milestone builds on it. The implementation plan must map every
acceptance criterion below to an exact milestone and verification command.

## V1 non-goals

V1 does not implement:

- a sophisticated archetype ECS or speculative throughput optimization;
- a graphical editor or general level-authoring module;
- a scripting or expression language;
- networking, replication, rollback, or authoritative server transport;
- procedural content tooling;
- arbitrary plugin loading;
- reflection-based component serialization or inspection;
- ownership of the application's Stage, Box2D world, input processor, render loop, camera, batch,
  textures, atlases, runtime, or MCP transports;
- a new gameplay MCP or duplicate of runtime/harness/markup responsibilities;
- unrestricted image recognition.

## Acceptance criteria

V1 is accepted when current evidence proves all of the following:

1. The four artifacts compile independently with the documented one-way dependency graph.
2. Agents can define the canonical fixture entities in strict JSON prefabs without repetitive
   entity-construction Java.
3. The compiled schedule is stable, inspectable, and rejects accidental ordering ambiguity.
4. Spawn, activation, update, despawn, disposal, and reset occur only at documented barriers.
5. Production input creates ordered commands consumed on fixed ticks.
6. The Box2D bridge has one documented authority for every degree of freedom and disposes only
   bridge-owned native objects.
7. Rendering and animation read gameplay state without becoming gameplay authority.
8. Runtime entities and events are automatically projected from standard components with explicit
   bounded registration and frame correlation.
9. Player and important world entities expose the documented visual semantics, including
   sprite/collider alignment evidence.
10. The fixture demonstrates movement, enemy, projectile, collision, damage, death, HUD, and reset.
11. The same seed and command transcript produce matching per-tick state and event digests.
12. Markup constructs every Scene2D UI and the real harness MCP drives it.
13. Runtime assertions, HUD runtime comparison, layout validation, production-control walkthrough,
    inspected screenshots, and first-playable evidence all pass their documented gates.
14. Maven publication metadata and archives validate locally, without staging or releasing.
15. The public GitHub `main` branch is clean, CI passes, and remote/local heads match.

The design is successful when adding an ordinary enemy naturally follows this path:

```text
prefab
-> standard components
-> existing phased systems
-> automatic runtime evidence
-> standard rendering
-> standard collision
-> deterministic verification
```

and does not require a bespoke manager graph, alternate update loop, duplicated domain state, or
test-only controller.
