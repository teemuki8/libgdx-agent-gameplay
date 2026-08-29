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
- `io.github.teemuki8:gameplay-box2d` — owner-thread Box2D 3 authority over an
  application-owned opaque world, genuine box/circle/capsule shapes, copied dynamics and
  collision-impact evidence, bounded private-identity revolute joints, forces, torque, and raycast.
  Native IDs, structs, pointers, closures, and buffers never cross the bridge API.
- `gameplay-fixture` is the markup/harness qualification application and is not published.

The Box2D 3 adapter reports maximum positive whole-step `totalNormalImpulse` as immutable
`CollisionImpact` events. Physical operations accept stable IDs and copied values only:

```java
bridge.applyForceToCenter(torsoId, new Vec2(forceXNewtons, forceYNewtons));
bridge.applyTorque(torsoId, torqueNewtonMetres);
bridge.applyForce(torsoId, forceNewtons, worldPointRenderUnits);
var hits = bridge.raycast(new Box2dRaycastSpec(origin, translation, category, mask, 16));
bridge.configureCollisionFilter(
        weaponId,
        new Box2dCollisionFilter(WEAPON_CATEGORY, DAMAGEABLE_MASK, 0));
```

Replacing a dynamic attachment is one owner-thread, unlocked-world transition. Copy the target
palm's state, disable the attached body, remove the old joint, activate the body from copied
palm-derived dynamics, and then create the replacement joint:

```java
Box2dBodyState palm = bridge.bodyState(palmId).orElseThrow();
bridge.deactivate(weaponId);
bridge.removeJoint(attachmentId);
bridge.activate(new Box2dBodyActivation(
        weaponId,
        palm.positionRenderUnits(),
        palm.angleRadians(),
        palm.velocityRenderUnitsPerSecond(),
        palm.angularVelocityRadiansPerSecond()));
bridge.createRevoluteJoint(new Box2dRevoluteJointSpec(
        attachmentId, palmId, weaponId, palm.positionRenderUnits(),
        lowerAngleRadians, upperAngleRadians, false));
```

`deactivate` retains the mapped native body and does not remove joints. `activate` accepts only a
disabled mapped dynamic body; it installs the copied pose and velocity before waking the body.
Forces and torque are finite SI values; positions, points, joint anchors, linear velocities, and
raycasts use render units. Angular values use radians.

Every bridge operation is owner-thread confined and bounded. Native mutations are rejected while
the world is locked. The bridge alone resolves native IDs, structs, pointers, closures, and
buffers. Application code passes stable gameplay IDs and immutable copied values and receives
copied body, joint, contact, and ray evidence; native identity never crosses the public API.

The `1.2.0` collision-filter API is additive over the `1.1.0` baseline. Building a release
candidate uses `-PapiBaselineVersion=1.1.0 -PreleaseVersion=1.2.0`; tagging, staging, and
publishing remain separately authorized operations.

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
