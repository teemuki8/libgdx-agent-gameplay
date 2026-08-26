# libGDX Agent Gameplay

[![CI](https://github.com/teemuki8/libgdx-agent-gameplay/actions/workflows/ci.yml/badge.svg)](https://github.com/teemuki8/libgdx-agent-gameplay/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.teemuki8/gameplay-core.svg)](https://central.sonatype.com/search?q=g%3Aio.github.teemuki8%20AND%20a%3Agameplay-core)

Deterministic, bounded gameplay construction for agent-driven libGDX games. The library provides
an ECS-lite world, fixed phase/slot ordering, typed commands and events, strict JSON prefabs,
stable replay digests, Scene2D-independent rendering data, agent-runtime projections, and an
authoritative Box2D bridge.

Requirements: JDK 25 and libGDX 1.14.2. The project uses its Gradle 9.7.0 wrapper.

## Smallest GL-free world

Add `io.github.teemuki8:gameplay-core:VERSION`, then create and step the world on one owner thread:

```java
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;

try (GameWorld world = GameWorld.builder(
        GameplayLimits.defaults(), StandardComponents.registry()).build()) {
    world.step();
    System.out.println(world.snapshot().tick());
}
```

The world contains no libGDX or GL dependency. Add adapters only where their ownership contracts
apply.

## Artifacts

- `io.github.teemuki8:gameplay-core` — GL-free world, components, commands, events, prefabs,
  schedules, snapshots, and deterministic replay.
- `io.github.teemuki8:gameplay-libgdx` — caller-owned camera/batch/atlas rendering and bounded
  fixed-step presentation support.
- `io.github.teemuki8:gameplay-runtime` — typed agent-runtime entity, event, visual, and correlation
  projections.
- `io.github.teemuki8:gameplay-box2d` — owner-thread Box2D authority, copied collision-impact
  evidence, bounded private-identity revolute joints/body forces, inspection, and explicit
  render-unit conversion. Native bodies, fixtures, and joints never cross the bridge API.
- `gameplay-fixture` is the markup/harness qualification application and is not published.

The Box2D adapter reports positive normal impulses as immutable `CollisionImpact` events. Its
joint/force API accepts stable IDs and copied values only: joint anchors use render units, while
forces, motor torque/speed, and angular limits use Box2D SI units/radians.

No Maven Central release has been authorized. Local publication qualification uses
`0.1.0-SNAPSHOT`; consumers should select an actual Central version once one exists.

## Guides

- [Getting started](docs/guides/getting-started.md)
- [Strict prefabs](docs/guides/prefabs.md)
- [libGDX integration](docs/guides/libgdx-integration.md)
- [Runtime evidence](docs/guides/runtime-evidence.md)
- [Box2D integration](docs/guides/box2d-integration.md)
- [Releasing](docs/guides/releasing.md)
- [First-playable evidence](docs/evidence/first-playable.md)

## Verification

```bash
./gradlew :gameplay-core:test
xvfb-run -a ./gradlew clean check javadoc apiCompatibility \
  verifyPublicationArchives verifyPublishedPoms --warning-mode=fail
./scripts/verify-maven-local.sh
```

The fixture also launches its fat JAR with `--mcp`, drives the real stdio harness with production
Enter/WASD/Space input, compares markup against independent runtime values, validates layout, and
verifies opaque screenshot receipts and cleanup.

Maven Central publication is irreversible and always requires separate release authorization.

Licensed under Apache-2.0. This independent project is not affiliated with the libGDX project.
