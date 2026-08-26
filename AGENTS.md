# libgdx-agent-gameplay engineering contract

This repository provides the constrained gameplay architecture between raw libGDX and
`libgdx-agent-runtime`. Optimize for predictable composition, deterministic execution, and
bounded agent-readable evidence—not maximum ECS throughput.

Read the approved V1 design and implementation plan before changing behavior:

- `docs/superpowers/specs/2026-08-10-libgdx-agent-gameplay-v1-design.md`
- `docs/superpowers/plans/2026-08-10-libgdx-agent-gameplay-v1.md`

The integration libraries remain authoritative for their own contracts:

1. `libgdx-ui-harness`: session lifecycle, render-thread scheduling, real input, frame fences,
   bounded stdio MCP artifacts, and semantic automation.
2. `libgdx-ui-markup`: XML/GDXCSS Scene2D construction, semantics by construction, and runtime
   binding correlation.
3. `libgdx-agent-runtime`: typed runtime values, frames, causes/events, UI correlation, and Box2D
   inspection.

Current baseline: JDK 25, Gradle wrapper 9.7.0, libGDX 1.14.2,
`libgdx-agent-runtime` 2.2.0, `libgdx-ui-harness` 1.2.1, and `libgdx-ui-markup` 0.5.0.

## Module boundaries

The four published modules and one non-published fixture are:

```text
gameplay-core
├── gameplay-libgdx
├── gameplay-runtime
└── gameplay-box2d

gameplay-fixture -> all four modules + markup + harness
```

- `gameplay-core` is GL-free. It owns IDs, components, limits, diagnostics, commands, events,
  fixed-tick scheduling, lifecycle, strict prefabs, immutable snapshots, replay digests, and the
  GL-free visual-evidence model.
- `gameplay-libgdx` owns fixed-step loop integration, logical asset resolution, rendering,
  animation presentation, camera projection, and visual snapshot production.
- `gameplay-runtime` owns explicit automatic projections into an application-owned
  `AgentRuntime`.
- `gameplay-box2d` owns stable mappings and bridge-created bodies/fixtures while the application
  owns the Box2D `World`.
- `gameplay-fixture` proves the complete contract and is never published.

Do not add reverse dependencies, a second gameplay/runtime authority, a new gameplay MCP, or a
parallel UI-construction path.

## Load-bearing rules

1. `GameWorld` is owner-thread confined. Completed deeply immutable snapshots may cross threads.
2. System order is compiled from stable phase plus numeric slot. Registration order is never a
   tie-breaker.
3. Structural mutation uses the documented activation, logical-removal, native-disposal, and
   reset barriers. Core components never own native resources.
4. Player, AI, replay, and future network intent enter through ordered commands. Systems do not
   poll input directly.
5. Prefabs use the closed `gameplay-prefabs/1` JSON schema and bounded Jackson streaming parse.
   Reject unknown/duplicate/trailing/oversized input; do not add inheritance, expressions,
   scripting, includes, URL loading, or reflection-based codecs.
6. Runtime values come from gameplay snapshots. UI widgets are not domain authority. Record a
   provable `UiFrameCorrelation` for every rendered frame; unproven evidence is unavailable, not
   guessed.
7. Rendering reads authoritative gameplay state. Presentation interpolation never flows back into
   gameplay, commands, physics, events, runtime authority, or deterministic digests.
8. Dynamic Box2D bodies own physics-backed pose/velocity after activation. Use one explicit unit
   conversion. Copy callback facts immediately; never retain `Contact` or expose native identity.
9. The application owns Stage, input multiplexer, render loop, camera, batch, atlas/textures,
   Box2D World, AgentRuntime, and MCP transport. Adapters dispose only resources they create.
10. Everything is bounded: entities, components, systems, commands, mutations, events, strings,
    prefab input, diagnostics, runtime values, visual entries, snapshots, artifacts, and deadlines.

## Fixture and UI

Every Scene2D UI in the fixture is constructed from XML and canonical `.gdxcss` through
`MarkupBuilder` and `HarnessSemanticSink`. Do not create a programmatic Actor-tree builder or bind
semantics imperatively.

The arena uses production WASD and Space input through the application input multiplexer. Harness
actions must dispatch real input; never call listeners or systems directly. Do not add visible
automation-only controls, teleports, locks, state skips, invulnerability, or alternate controllers.

For any UI- or state-bearing change, run the real application over stdio MCP: discover the fixed
session, query, act, wait, assert, compare runtime values, validate layout, capture screenshots,
and clean up. Advance the harness frame fence on every rendered frame, including title, game-over,
paused, reset, and visually unchanged frames. The wait snapshot supplier must route through the
render-thread scheduler.

Gameplay changes also require a production-control walkthrough, original-resolution screenshot
inspection, and an updated `docs/evidence/first-playable.md`. A startup smoke or
`CHECK_UNAVAILABLE` is not gameplay proof.

## Development and verification

Use red-green-refactor for every behavior change. The failing test must exercise public behavior,
real input, or real MCP transport and fail for the expected missing behavior before production
code is written. Keep test-only helpers out of production classes.

Run the narrowest applicable rung first:

```bash
./gradlew :gameplay-core:test --warning-mode=fail
xvfb-run -a ./gradlew :gameplay-libgdx:test :gameplay-runtime:test :gameplay-box2d:test --warning-mode=fail
xvfb-run -a ./gradlew :gameplay-fixture:test --warning-mode=fail
xvfb-run -a ./gradlew clean check javadoc --warning-mode=fail
```

Use only the wrapper. Compile with JDK 25, `-Xlint:all`, and `-Werror`; Javadocs are warning-free.
After a wrapper, plugin, or dependency change, run:

```bash
./scripts/refresh-verification-metadata.sh --write-locks --no-daemon --console=plain
git diff -- gradle/verification-metadata.xml '**/gradle.lockfile'
```

The refresh discovers candidates; review and commit only trusted additions. Ordinary builds and
IDE sync never auto-trust dependencies.

## Release boundary

Maven Central publication is irreversible. Ordinary implementation, repository creation, CI
repair, or release readiness does not authorize a version tag, GitHub release, Central staging,
Central publish/drop action, or release-workflow dispatch. Those actions require separate explicit
authorization.

Only `gameplay-core`, `gameplay-libgdx`, `gameplay-runtime`, and `gameplay-box2d` are publishable.
`gameplay-fixture` is qualification-only and must never enter a library JAR, POM, or Central
deployment. Initial API compatibility tasks explicitly skip until a real released baseline is
supplied with `-PapiBaselineVersion`; never invent a baseline. Disposable
`qualificationRepository` publication is consumer verification, not distribution.
