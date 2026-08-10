# libGDX Agent Gameplay V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and publicly publish the verified `teemuki8/libgdx-agent-gameplay` repository with four publication-ready Maven modules and one deterministic, agent-drivable top-down qualification game, without creating a release or publishing to Maven Central.

**Architecture:** A GL-free ECS-lite core owns immutable components, strict JSON prefabs, explicit fixed-tick scheduling, staged lifecycle, commands, events, and evidence models. Narrow libGDX, agent-runtime, and Box2D adapters preserve application ownership while one non-published fixture proves the complete path through markup, harness, runtime evidence, physics, rendering, and deterministic reruns.

**Tech Stack:** JDK 25; Gradle wrapper 9.6.1; libGDX/Box2D 1.14.2; Jackson Core 2.22.1; libgdx-agent-runtime 2.1.0; libgdx-ui-harness 1.2.1; libgdx-ui-markup 0.5.0; JUnit Jupiter 6.1.2; LWJGL3/Xvfb; GitHub Actions; Maven Central user-managed staging workflows.

## Global Constraints

- Repository and GitHub target: public `teemuki8/libgdx-agent-gameplay`, default branch `main`.
- Maven group: `io.github.teemuki8`; artifacts: `gameplay-core`, `gameplay-libgdx`, `gameplay-runtime`, and `gameplay-box2d`.
- Current dependency baseline: JDK 25, Gradle wrapper 9.6.1, libGDX 1.14.2, agent runtime 2.1.0, harness 1.2.1, markup 0.5.0, Jackson Core 2.22.1.
- Use the Gradle wrapper only. Linux GL/native verification always runs under `xvfb-run -a`.
- Compile Java with `--release 25`, `-Xlint:all`, and `-Werror`; Javadocs use doclint and `-Werror`.
- `gameplay-core` remains free of libGDX, Box2D, Scene2D, harness, and agent-runtime dependencies.
- No reflection, annotation scanning, class-name loading, arbitrary object traversal, scripting, URL loading, caller-selected filesystem reads, native-pointer identity, or unrestricted image recognition.
- Every public model is immutable, validated, deterministically ordered, deeply bounded, and warning-free with Javadocs.
- `GameWorld` and every libGDX/Box2D/runtime registration mutation are owner-thread confined. Completed immutable snapshots may cross threads.
- Application ownership is preserved for Stage, input processor/multiplexer, render loop, camera, SpriteBatch, assets, Box2D World, AgentRuntime, and MCP transports.
- UI construction is markup-only through XML/GDXCSS; harness actions use the real input path and never invoke listeners directly.
- Runtime values come from gameplay snapshots. `MarkupRuntimeSource.registerBindings` installs UI correlation only; widget readback is never domain authority.
- Frame fences and runtime/UI correlation advance on every rendered frame, including title, reset, paused, and unchanged frames.
- Production code follows red-green-refactor. Each behavioral step records the focused failing test before implementation and reruns the affected suite after green/refactor.
- Dependency changes require `./scripts/refresh-verification-metadata.sh --no-daemon --console=plain`, review of `gradle/verification-metadata.xml`, and trusted additions only.
- The final Linux gate is `xvfb-run -a ./gradlew clean check javadoc --warning-mode=fail`.
- Do not create a version tag, GitHub release, Central deployment, or Maven Central publication. Separate release authorization is required.

## File and package map

All Java packages begin with `io.github.teemuki8.libgdx.agent.gameplay`.

| Area | Files/responsibility |
| --- | --- |
| Root build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper 9.6.1, dependency verification, Checkstyle, publication archive/POM validation |
| Core values | `gameplay-core/.../core/value/*` for IDs, `Vec2`, `Bounds2`, `Rgba`, finite/bounded validation |
| Components | `gameplay-core/.../core/component/*` for marker/type/registry and nine standard immutable components |
| Commands/events | `gameplay-core/.../core/command/*`, `core/event/*` for typed envelopes, buffers, standard payloads, attribution |
| World/schedule | `gameplay-core/.../core/world/*`, `core/system/*` for entity views/drafts, schedule, context, barriers, snapshots, reset |
| Prefabs | `gameplay-core/.../core/prefab/*`, `gameplay-core/src/main/resources/schema/gameplay-prefabs.schema.json` for bounded Jackson streaming parse and explicit codecs |
| Determinism | `gameplay-core/.../core/replay/*` for command transcripts and canonical SHA-256 tick/event digests |
| libGDX | `gameplay-libgdx/.../libgdx/*` for fixed-step loop, asset resolver, renderer, animation, projection, visual snapshot production |
| Runtime | `gameplay-runtime/.../runtime/*` for explicit projection registry, open/capture systems, entity source, events and causes |
| Box2D | `gameplay-box2d/.../box2d/*` for unit conversion, body/fixture mapping, contacts, authority, inspection registrations, disposal |
| Fixture | `gameplay-fixture/.../fixture/*`, `resources/gameplay`, `resources/ui`, and `resources/art` for the canonical arena game |
| Qualification | fixture tests including the real stdio MCP black-box client, bounded session artifacts under `build/artifacts`, and reviewed evidence under `docs/evidence` |
| CI/release | `.github/workflows/ci.yml`, `stage-maven-central.yml`, `manage-maven-central.yml`; all third-party actions pinned by full SHA |

---

## Milestone 1 — Repository foundation and deterministic core

### Task 1: Bootstrap the standalone multi-module build

**Files:**
- Create: `.gitattributes`
- Create: `.gitignore`
- Create: `AGENTS.md`
- Create: `LICENSE`
- Create: `NOTICE`
- Create: `README.md`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` from the verified bootstrap wrapper 9.6.1
- Create: `config/checkstyle/checkstyle.xml`
- Create: `config/checkstyle/suppressions.xml`
- Create: `gameplay-core/build.gradle.kts`
- Create: `gameplay-libgdx/build.gradle.kts`
- Create: `gameplay-runtime/build.gradle.kts`
- Create: `gameplay-box2d/build.gradle.kts`
- Create: `gameplay-fixture/build.gradle.kts`
- Create: `scripts/refresh-verification-metadata.sh`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/BuildContractTest.java`

**Interfaces:**
- Consumes: approved design spec and verified version baselines.
- Produces: five Gradle projects; JDK 25/warnings-as-errors convention; dependency locks; root `javadoc`, `verifyStackVersionContract`, and `verifyPublicationArchives` tasks.

- [ ] **Step 1: Install the exact wrapper and minimal module build (build-only scaffolding, no production behavior)**

Use the existing verified wrapper files, then create the settings/version catalog with these exact coordinates:

```kotlin
// settings.gradle.kts
import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "libgdx-agent-gameplay"
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
include("gameplay-core", "gameplay-libgdx", "gameplay-runtime", "gameplay-box2d", "gameplay-fixture")
```

```toml
# gradle/libs.versions.toml
[versions]
gdx = "1.14.2"
jackson = "2.22.1"
agent-runtime = "2.1.0"
harness = "1.2.1"
markup = "0.5.0"
junit = "6.1.2"
slf4j = "2.0.17"

[libraries]
gdx-core = { module = "com.badlogicgames.gdx:gdx", version.ref = "gdx" }
gdx-lwjgl3 = { module = "com.badlogicgames.gdx:gdx-backend-lwjgl3", version.ref = "gdx" }
gdx-box2d = { module = "com.badlogicgames.gdx:gdx-box2d", version.ref = "gdx" }
gdx-platform = { module = "com.badlogicgames.gdx:gdx-platform", version.ref = "gdx" }
gdx-box2d-platform = { module = "com.badlogicgames.gdx:gdx-box2d-platform", version.ref = "gdx" }
jackson-core = { module = "com.fasterxml.jackson.core:jackson-core", version.ref = "jackson" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
agent-runtime-core = { module = "io.github.teemuki8:agent-runtime-core", version.ref = "agent-runtime" }
agent-runtime-box2d = { module = "io.github.teemuki8:agent-runtime-box2d", version.ref = "agent-runtime" }
harness-lwjgl3 = { module = "io.github.teemuki8:harness-lwjgl3", version.ref = "harness" }
harness-mcp = { module = "io.github.teemuki8:harness-mcp", version.ref = "harness" }
harness-protocol = { module = "io.github.teemuki8:harness-protocol", version.ref = "harness" }
harness-agent-runtime = { module = "io.github.teemuki8:harness-agent-runtime", version.ref = "harness" }
markup-core = { module = "io.github.teemuki8:libgdx-ui-markup", version.ref = "markup" }
markup-harness = { module = "io.github.teemuki8:libgdx-ui-markup-harness", version.ref = "markup" }
markup-runtime = { module = "io.github.teemuki8:libgdx-ui-markup-runtime", version.ref = "markup" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit" }
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }
```

The root build applies `java-library`, Checkstyle, JaCoCo, dependency locking, JDK 25, reproducible archives, sources/Javadoc JARs, `-Xlint:all -Werror`, and Javadoc doclint/Werror to every subproject. `gameplay-fixture` is excluded from `maven-publish` and signing.

- [ ] **Step 2: Verify Gradle sees the isolated project**

Run: `./gradlew projects --no-daemon --console=plain --warning-mode=fail`

Expected: PASS; all five projects are listed and the parent bootstrap project is absent.

- [ ] **Step 3: Write the failing build contract test**

```java
package io.github.teemuki8.libgdx.agent.gameplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BuildContractTest {
    @Test void pinsTheApprovedStackAndKeepsCoreGlFree() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        String versions = Files.readString(root.resolve("gradle/libs.versions.toml"));
        String core = Files.readString(root.resolve("gameplay-core/build.gradle.kts"));
        assertTrue(versions.contains("gdx = \"1.14.2\""));
        assertTrue(versions.contains("agent-runtime = \"2.1.0\""));
        assertTrue(versions.contains("harness = \"1.2.1\""));
        assertTrue(versions.contains("markup = \"0.5.0\""));
        assertTrue(versions.contains("jackson = \"2.22.1\""));
        assertTrue(core.contains("implementation(libs.jackson.core)"));
        assertFalse(core.contains("libs.gdx"));
        assertFalse(core.contains("agent.runtime"));
        assertEquals("9.6.1", Files.readString(root.resolve(
                "gradle/wrapper/gradle-wrapper.properties")).lines()
                .filter(line -> line.startsWith("distributionUrl="))
                .findFirst().orElseThrow().replaceAll(".*gradle-([0-9.]+)-bin.*", "$1"));
    }
}
```

- [ ] **Step 4: Run the focused test and observe the intended failure**

Run: `./gradlew :gameplay-core:test --tests '*BuildContractTest' --no-daemon --console=plain`

Expected: FAIL because `gameplay-core/build.gradle.kts` does not yet declare Jackson Core.

- [ ] **Step 5: Complete module dependencies and root contract tasks**

Use these dependency directions:

```kotlin
// gameplay-core
dependencies { implementation(libs.jackson.core) }

// gameplay-libgdx
dependencies { api(project(":gameplay-core")); api(libs.gdx.core) }

// gameplay-runtime
dependencies { api(project(":gameplay-core")); api(libs.agent.runtime.core) }

// gameplay-box2d
dependencies {
    api(project(":gameplay-core"))
    api(libs.gdx.box2d)
    api(libs.agent.runtime.box2d)
}

// gameplay-fixture
dependencies {
    implementation(project(":gameplay-core"))
    implementation(project(":gameplay-libgdx"))
    implementation(project(":gameplay-runtime"))
    implementation(project(":gameplay-box2d"))
    implementation(libs.gdx.lwjgl3)
    implementation(libs.harness.lwjgl3)
    implementation(libs.harness.mcp)
    implementation(libs.harness.protocol)
    implementation(libs.harness.agent.runtime)
    implementation(libs.markup.core)
    implementation(libs.markup.harness)
    implementation(libs.markup.runtime)
    testImplementation(libs.jackson.databind)
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    runtimeOnly(variantOf(libs.gdx.box2d.platform) { classifier("natives-desktop") })
    runtimeOnly(libs.slf4j.nop)
}
```

Implement `verifyStackVersionContract` by reading the version catalog and asserting the exact six approved stack versions. Configure publication coordinates but leave the version at `0.1.0-SNAPSHOT` unless `-PreleaseVersion` is supplied.

- [ ] **Step 6: Run build contracts and generate trusted locks/metadata**

Run:

```bash
./gradlew :gameplay-core:test --tests '*BuildContractTest' --write-locks --no-daemon --console=plain
./scripts/refresh-verification-metadata.sh --no-daemon --console=plain
git diff -- gradle/verification-metadata.xml '**/gradle.lockfile'
```

Expected: focused test PASS; five lockfiles exist; metadata contains only artifacts resolved by the declared build.

- [ ] **Step 7: Commit the foundation**

```bash
git add .gitattributes .gitignore AGENTS.md LICENSE NOTICE README.md settings.gradle.kts \
  build.gradle.kts gradle.properties gradle config gameplay-core/build.gradle.kts \
  gameplay-libgdx/build.gradle.kts gameplay-runtime/build.gradle.kts \
  gameplay-box2d/build.gradle.kts gameplay-fixture/build.gradle.kts scripts gradlew gradlew.bat \
  gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/BuildContractTest.java
git commit -m "build: bootstrap gameplay modules"
```

### Task 2: Add bounded core values and standard components

**Files:**
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/GameplayLimits.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/IdentifierRules.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/EntityId.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/EntityIdAllocator.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/PrefabId.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/SystemId.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/CommandSourceId.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/Vec2.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/Bounds2.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/value/Rgba.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Component.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/ComponentType.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/ComponentRegistry.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/StandardComponents.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Transform2D.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Movement.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Health.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Faction.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Lifetime.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Collider.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Sprite.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/AnimationClip.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Animation.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/Render.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/diagnostic/GameplayDiagnosticCode.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/diagnostic/GameplayDiagnostic.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/diagnostic/GameplayException.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/StandardComponentsTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/component/ComponentRegistryTest.java`

**Interfaces:**
- Consumes: `GameplayLimits.defaults()` from this task only.
- Produces: `Component`, `ComponentType<T>`, immutable standard records, `StandardComponents.registry()`, GL-free geometry/color values.

- [ ] **Step 1: Write failing value/component behavior tests**

```java
@Test void standardValuesAreImmutableAndValidated() {
    Transform2D transform = new Transform2D(
            new Vec2(2.0, 3.0), 0.0, new Vec2(0.8, 1.1), new Vec2(0.5, 0.5));
    Health health = new Health(30, 30);
    assertEquals(new Vec2(0.8, 1.1), transform.size());
    assertEquals(30, health.current());
    assertThrows(GameplayException.class,
            () -> new Vec2(Double.NaN, 0.0));
    assertThrows(GameplayException.class,
            () -> new Health(31, 30));
    assertThrows(GameplayException.class,
            () -> new Transform2D(Vec2.ZERO, 0.0, new Vec2(1, 1), new Vec2(1.1, 0.5)));
}

@Test void registryRejectsDuplicateStableTypeIds() {
    ComponentRegistry registry = ComponentRegistry.builder()
            .register(new ComponentType<>("health", Health.class))
            .build();
    assertThrows(GameplayException.class, () -> ComponentRegistry.builder()
            .register(new ComponentType<>("health", Health.class))
            .register(new ComponentType<>("health", Health.class))
            .build());
    assertEquals(Health.class, registry.require("health").valueClass());
}
```

Add an allocator test that yields `projectile-0001`, `projectile-0002`, rejects an invalid prefix, resets at an explicit reset boundary, and then yields `projectile-0001` again.

- [ ] **Step 2: Run tests and observe missing-type failures**

Run: `./gradlew :gameplay-core:test --tests '*StandardComponentsTest' --tests '*ComponentRegistryTest'`

Expected: compilation FAIL because the value/component API does not exist.

- [ ] **Step 3: Implement the minimal immutable API**

Use these exact public shapes:

```java
public interface Component { }

public record ComponentType<T extends Component>(String id, Class<T> valueClass) { }

public record Vec2(double x, double y) {
    public static final Vec2 ZERO = new Vec2(0.0, 0.0);
}

public record Transform2D(Vec2 position, double rotationRadians, Vec2 size, Vec2 pivot)
        implements Component { }

public record Movement(Vec2 velocity, double maxSpeed) implements Component { }
public record Health(long current, long max) implements Component { }
public record Faction(String value) implements Component { }
public record Lifetime(long remainingTicks) implements Component { }
public record Collider(Shape shape, Vec2 size, Vec2 offset, boolean sensor,
        int categoryBits, int maskBits) implements Component {
    public enum Shape { BOX, CIRCLE }
}
public record Sprite(String asset, String region, Vec2 visualSize, Vec2 origin)
        implements Component { }
public record AnimationClip(List<String> frames, long frameDurationTicks, boolean loop) { }
public record Animation(Map<String, AnimationClip> clips, String currentClip,
        long elapsedTicks, int frameIndex) implements Component { }
public record Render(String layer, int order, Rgba tint, boolean visible) implements Component { }
```

Every constructor validates non-null values, finite numbers, positive sizes/durations, normalized pivots/origins, health ranges, 16-bit collision masks, copied ordered maps/lists, and identifier bounds. `EntityIdAllocator` produces zero-padded stable IDs such as `projectile-0001` and resets only at the world reset boundary. `GameplayLimits.defaults()` returns the exact maxima from the design spec.

All trust-boundary failures carry a `GameplayDiagnostic` with stable `GameplayDiagnosticCode`, retryability, operation/lifecycle state, applicable tick/entity/prefab/component/command/system identity, JSON pointer/source location, expected and bounded observed values, a correction capped at 512 characters, and an optional correlation ID. `GameplayException.code()` delegates to the diagnostic code. Diagnostics never expose stack traces, native addresses, credentials, arbitrary paths, or unbounded application messages.

- [ ] **Step 4: Run focused tests and Javadocs**

Run: `./gradlew :gameplay-core:test --tests '*StandardComponentsTest' --tests '*ComponentRegistryTest' :gameplay-core:javadoc --warning-mode=fail`

Expected: PASS with no compiler/Javadoc warnings.

- [ ] **Step 5: Commit core values/components**

```bash
git add gameplay-core/src/main gameplay-core/src/test
git commit -m "feat: add bounded gameplay components"
```

### Task 3: Add ordered commands and typed gameplay events

**Files:**
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/GameplayCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/CommandEnvelope.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/CommandBuffer.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/MoveCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/AimCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/FireCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/InteractCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/UseAbilityCommand.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/GameplayEvent.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EventAttributeValue.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EventAttributes.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EventEnvelope.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EventBuffer.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EntitySpawned.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EntityDespawned.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/DamageApplied.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EntityKilled.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/ItemCollected.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/ProjectileCreated.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/ObjectiveCompleted.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/command/CommandBufferTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/event/EventBufferTest.java`

**Interfaces:**
- Consumes: `EntityId`, `CommandSourceId`, `Vec2`, `GameplayLimits`.
- Produces: ordered `CommandEnvelope`, `CommandBuffer.commandsFor(long)`, attributed `EventEnvelope`, per-tick `EventBuffer`.

- [ ] **Step 1: Write failing ordering and bound tests**

```java
@Test void commandsUseSourceAndSequenceNotRegistrationOrder() {
    CommandBuffer buffer = new CommandBuffer(GameplayLimits.defaults());
    buffer.enqueue(new CommandEnvelope(7, CommandSourceId.of("player-b"), 0,
            new FireCommand(EntityId.of("player"), Vec2.ZERO, new Vec2(1, 0))));
    buffer.enqueue(new CommandEnvelope(7, CommandSourceId.of("player-a"), 1,
            new MoveCommand(EntityId.of("player"), new Vec2(0, 1))));
    buffer.enqueue(new CommandEnvelope(7, CommandSourceId.of("player-a"), 0,
            new MoveCommand(EntityId.of("player"), new Vec2(1, 0))));
    assertEquals(List.of("player-a:0", "player-a:1", "player-b:0"),
            buffer.commandsFor(7).stream()
                    .map(value -> value.source().value() + ":" + value.sequence()).toList());
}

@Test void duplicateAndLateCommandsFailClosed() {
    CommandBuffer buffer = new CommandBuffer(GameplayLimits.defaults());
    CommandEnvelope command = new CommandEnvelope(2, CommandSourceId.of("player"), 0,
            new MoveCommand(EntityId.of("player"), Vec2.ZERO));
    buffer.enqueue(command);
    assertThrows(GameplayException.class, () -> buffer.enqueue(command));
    buffer.advanceTo(3);
    assertThrows(GameplayException.class, () -> buffer.enqueue(new CommandEnvelope(
            2, CommandSourceId.of("player"), 1,
            new MoveCommand(EntityId.of("player"), Vec2.ZERO))));
}
```

Add an event test that opens tick 3, appends `DamageApplied` and `EntityKilled`, checks sequences 0/1, closes the tick, and proves append-after-close plus the 4,096-event limit return typed failures.

- [ ] **Step 2: Run focused tests and observe compilation failure**

Run: `./gradlew :gameplay-core:test --tests '*CommandBufferTest' --tests '*EventBufferTest'`

Expected: compilation FAIL because command/event classes do not exist.

- [ ] **Step 3: Implement command/event contracts**

Use non-sealed marker interfaces so applications can add compile-time typed commands/events without reflection:

```java
public interface GameplayCommand { }
public record CommandEnvelope(long targetTick, CommandSourceId source, long sequence,
        GameplayCommand command) { }
public interface GameplayEvent { }
public record EventEnvelope(long tick, long sequence, GameplayEvent event,
        EventAttributes attributes) { }
public record MoveCommand(EntityId entityId, Vec2 direction) implements GameplayCommand { }
public record FireCommand(EntityId entityId, Vec2 origin, Vec2 direction)
        implements GameplayCommand { }
public record DamageApplied(EntityId subject, EntityId source, long amount)
        implements GameplayEvent { }
public record EntityKilled(EntityId subject, EntityId source) implements GameplayEvent { }
```

`CommandBuffer` stores a bounded future window, rejects duplicate `(source, sequence)`, and returns copied sorted lists. `EventAttributeValue` is a closed GL-free scalar/entity-ID value family; `EventAttributes` is a copied, key-sorted, string/value-bounded map used for evidence such as stable contact IDs. `EventBuffer` requires one open tick, assigns sequence immediately, and returns immutable completed events.

- [ ] **Step 4: Run focused and core suites**

Run: `./gradlew :gameplay-core:test --tests '*CommandBufferTest' --tests '*EventBufferTest' :gameplay-core:test`

Expected: PASS.

- [ ] **Step 5: Commit commands/events**

```bash
git add gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/{command,event} \
  gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/{command,event}
git commit -m "feat: add ordered gameplay commands and events"
```

### Task 4: Implement the schedule, world lifecycle, snapshots, and reset

**Files:**
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/SystemPhase.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/SystemDescriptor.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/GameSystem.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/SystemSchedule.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/SystemContext.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/EntityDraft.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/EntityView.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/EntityState.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/EntitySnapshot.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/WorldSnapshot.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/CompletedTick.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/LifecycleParticipant.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/WorldInitializer.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/SpawnSink.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/GameWorld.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/system/SystemScheduleTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/GameWorldLifecycleTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/world/GameWorldThreadTest.java`

**Interfaces:**
- Consumes: component registry, commands/events, limits.
- Produces: immutable compiled `SystemSchedule`, owner-thread `GameWorld`, staged spawn/despawn/disposal, `WorldSnapshot`, `CompletedTick`, reset initializer, typed diagnostics.

- [ ] **Step 1: Write failing schedule tests**

```java
@Test void scheduleSortsByPhaseAndSlotAndRejectsAmbiguity() {
    GameSystem gameplay = system("combat", SystemPhase.GAMEPLAY, 20);
    GameSystem input = system("commands", SystemPhase.INPUT, 10);
    SystemSchedule schedule = SystemSchedule.compile(List.of(gameplay, input),
            GameplayLimits.defaults());
    assertEquals(List.of("commands", "combat"), schedule.descriptors().stream()
            .map(value -> value.id().value()).toList());
    assertThrows(GameplayException.class, () -> SystemSchedule.compile(List.of(
            system("movement", SystemPhase.GAMEPLAY, 20), gameplay),
            GameplayLimits.defaults()));
}
```

- [ ] **Step 2: Run schedule test and confirm missing API failure**

Run: `./gradlew :gameplay-core:test --tests '*SystemScheduleTest'`

Expected: compilation FAIL because schedule types do not exist.

- [ ] **Step 3: Implement the immutable schedule**

```java
public enum SystemPhase {
    INPUT, PRE_PHYSICS, PHYSICS, POST_PHYSICS, GAMEPLAY, ANIMATION,
    RENDER_PREP, RUNTIME_CAPTURE
}
public record SystemDescriptor(SystemId id, SystemPhase phase, int slot) { }
public interface GameSystem {
    SystemDescriptor descriptor();
    void update(SystemContext context);
}
```

Compilation validates IDs/counts/slot range, rejects duplicate IDs and phase/slot pairs, sorts once, and exposes copied descriptors.

- [ ] **Step 4: Write failing lifecycle/barrier tests**

```java
@Test void spawnAndDespawnUseDocumentedBarriers() {
    List<String> lifecycle = new ArrayList<>();
    LifecycleParticipant observer = observer(lifecycle);
    GameWorld world = worldWith(observer, context -> {
        if (context.tick() == 0) context.spawn(playerDraft());
        if (context.tick() == 2) context.despawn(EntityId.of("player"));
    });
    assertTrue(world.entity(EntityId.of("player")).isEmpty());
    world.step();
    assertTrue(world.entity(EntityId.of("player")).isEmpty());
    world.step();
    assertEquals(EntityState.ACTIVE, world.entity(EntityId.of("player")).orElseThrow().state());
    world.step();
    assertTrue(world.entity(EntityId.of("player")).isEmpty());
    assertEquals(List.of("activate:player", "logical-despawn:player", "dispose:player"), lifecycle);
}

@Test void resetRunsOnlyAtBoundaryAndReplaysInitializer() {
    GameWorld world = worldWithInitializer(sink -> sink.spawn(playerDraft()));
    world.step();
    world.requestReset();
    world.step();
    assertTrue(world.entity(EntityId.of("player")).isEmpty());
    CompletedTick firstResetTick = world.step();
    assertEquals(0, firstResetTick.snapshot().tick());
    assertTrue(firstResetTick.snapshot().entity(EntityId.of("player")).isPresent());
}
```

Add a thread test that calls `world.snapshot()` from a second thread (allowed after completion) and `world.spawn(...)` from it (typed `OWNER_THREAD_VIOLATION`).

- [ ] **Step 5: Run lifecycle tests and observe failures**

Run: `./gradlew :gameplay-core:test --tests '*GameWorldLifecycleTest' --tests '*GameWorldThreadTest'`

Expected: compilation FAIL because `GameWorld` and lifecycle types do not exist.

- [ ] **Step 6: Implement GameWorld with typed lifecycle failures**

Use this public API:

```java
public final class GameWorld implements AutoCloseable {
    public static Builder builder(GameplayLimits limits, ComponentRegistry components);
    public long tick();
    public SystemSchedule schedule();
    public Optional<EntityView> entity(EntityId id);
    public List<EntityView> query(ComponentType<?>... required);
    public void enqueue(CommandEnvelope command);
    public void spawn(EntityDraft draft);
    public void despawn(EntityId id);
    public void requestReset();
    public CompletedTick step();
    public WorldSnapshot snapshot();
    @Override public void close();
}

public interface SystemContext {
    long tick();
    long fixedStepNanos();
    List<EntityView> query(ComponentType<?>... required);
    <T extends Component> void replace(EntityId id, ComponentType<T> type, T value);
    List<CommandEnvelope> commands();
    void emit(GameplayEvent event);
    void emit(GameplayEvent event, EventAttributes attributes);
    void spawn(EntityDraft draft);
    void despawn(EntityId id);
}
```

Apply start activation, phase execution, post-GAMEPLAY logical removal, `RENDER_PREP`, `RUNTIME_CAPTURE`, post-capture native disposal, and reset in that exact order. Snapshot component maps are sorted by component ID and defensively copied.

- [ ] **Step 7: Run all core tests and Javadocs**

Run: `./gradlew :gameplay-core:test :gameplay-core:javadoc --warning-mode=fail`

Expected: PASS with no warnings.

- [ ] **Step 8: Commit the deterministic world**

```bash
git add gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/{system,world} \
  gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/{system,world}
git commit -m "feat: add deterministic gameplay world"
```

## Milestone 2: Strict Prefabs and Replay Proof

### Task 5: Add the bounded JSON prefab contract

**Files:**

- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabLimits.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabValue.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/ComponentFields.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/ComponentCodec.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/ComponentCodecRegistry.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabDefinition.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabCatalog.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabParser.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/StandardComponentCodecs.java`
- Create: `gameplay-core/src/main/resources/schema/gameplay-prefabs.schema.json`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabParserTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab/PrefabInstantiationTest.java`

- [ ] **Step 1: Write strict parser tests first**

Cover one valid catalog and one case for every rejection class: duplicate prefab/component/field, unknown component/field, missing required field, wrong JSON type, non-finite number, out-of-range value, trailing content, excessive bytes/depth/count/string length, and accumulated diagnostic overflow.

```java
@Test void rejectsUnknownFieldWithLocationAndCorrection() {
    GameplayException failure = assertThrows(GameplayException.class,
        () -> parser().parse(bytes("""
            {"schemaVersion":"gameplay-prefabs/1","prefabs":[
              {"id":"player","components":[
                {"type":"health","curent":3,"max":3}
              ]}
            ]}
            """)));
    assertEquals(GameplayDiagnosticCode.UNKNOWN_PREFAB_FIELD, failure.code());
    assertEquals("/prefabs/0/components/0/curent", failure.diagnostic().jsonPointer());
    assertTrue(failure.diagnostic().correction().contains("current"));
}

@Test void acceptsOnlyFiniteBoundedValues() {
    GameplayException failure = assertThrows(GameplayException.class,
        () -> parser().parse(bytes(prefabWithHealth("1e309"))));
    assertEquals(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE, failure.code());
}
```

- [ ] **Step 2: Run parser tests and observe failure**

Run: `./gradlew :gameplay-core:test --tests '*PrefabParserTest'`

Expected: compilation FAIL because the prefab API does not exist.

- [ ] **Step 3: Implement the public codec and parser API**

```java
public interface ComponentCodec<T extends Component> {
    ComponentType<T> type();
    Set<String> acceptedFields();
    T decode(ComponentFields fields);
}

public final class PrefabParser {
    public PrefabParser(ComponentCodecRegistry codecs, PrefabLimits limits);
    public PrefabCatalog parse(byte[] utf8Json);
}

public record PrefabDefinition(String id, Map<ComponentType<?>, Component> components) {
    public EntityDraft instantiate(EntityId entityId);
    public EntityDraft instantiate(EntityId entityId, Transform2D transformOverride);
}
```

Parse the exact array-shaped `gameplay-prefabs/1` document through a streaming Jackson reader with duplicate detection enabled. Arrays preserve duplicate prefab/component IDs so they can be diagnosed. Reject rather than ignore every unsupported schema version, duplicate JSON key, duplicate prefab/component ID, unknown field/type, incompatible component combination, invalid enum, trailing token, or unrecognized value. Apply the fixed limits from the design: 1 MiB input, depth 32, 1,024 prefabs, 64 components per prefab, 256-character strings, 256 array entries, 128-character numeric tokens, 64 diagnostics, and 512 characters per correction. Keep all parser/model work GL-free.

- [ ] **Step 4: Add schema and standard codecs**

The schema and codecs define exactly the standard component IDs and fields from the design. Keep the schema closed with `additionalProperties: false`; it is documentation/tooling parity, while `PrefabParser` remains the runtime authority.

- [ ] **Step 5: Test deterministic instantiation and defensive copies**

Assert two instantiations have identical sorted component values but independent immutable maps, and that only `Transform2D` can be overridden by the convenience overload.

- [ ] **Step 6: Run the prefab rung**

Run: `./gradlew :gameplay-core:test --tests '*Prefab*' :gameplay-core:javadoc --warning-mode=fail`

Expected: PASS.

- [ ] **Step 7: Commit strict prefabs**

```bash
git add gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab \
  gameplay-core/src/main/resources/schema \
  gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/prefab
git commit -m "feat: add strict bounded prefab loading"
```

### Task 6: Add canonical snapshots, transcripts, and determinism digests

**Files:**

- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/CanonicalWorldEncoder.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/WorldDigest.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/CommandTranscript.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/TranscriptRunner.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/TranscriptResult.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/random/DeterministicRandom.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/DeterminismTest.java`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay/CanonicalWorldEncoderTest.java`

- [ ] **Step 1: Write replay tests first**

```java
@Test void identicalSeedAndTranscriptProduceIdenticalDigestAtEveryTick() {
    CommandTranscript transcript = arenaTranscript();
    TranscriptResult first = runner(42).run(transcript, 240);
    TranscriptResult second = runner(42).run(transcript, 240);
    assertEquals(first.tickDigests(), second.tickDigests());
    assertEquals(first.eventDigests(), second.eventDigests());
}

@Test void oneChangedCommandChangesTheFirstAffectedDigest() {
    TranscriptResult left = runner(42).run(arenaTranscript(), 240);
    TranscriptResult right = runner(42).run(arenaTranscriptWithChangedAim(), 240);
    assertNotEquals(left.tickDigests().get(31), right.tickDigests().get(31));
}
```

- [ ] **Step 2: Run replay tests and observe failure**

Run: `./gradlew :gameplay-core:test --tests '*DeterminismTest' --tests '*CanonicalWorldEncoderTest'`

Expected: compilation FAIL because replay classes do not exist.

- [ ] **Step 3: Implement canonical encoding and digesting**

Encode UTF-8 records in this order: tick, sorted entity ID, entity state, sorted component type ID, component field order owned by its codec. Encode event records by tick-local sequence, event type, subject/source, then sorted typed attributes. Encode floating-point values from canonical IEEE-754 bits, normalize `-0.0` to `0.0`, reject non-finite values before encoding, cap output at 4 MiB, then calculate SHA-256. `TranscriptResult` exposes both per-tick world digests and per-tick event digests.

```java
public record WorldDigest(long tick, String sha256) {}

public final class TranscriptRunner {
    public TranscriptRunner(Supplier<GameWorld> worldFactory);
    public TranscriptResult run(CommandTranscript transcript, long ticks);
}
```

`DeterministicRandom` wraps a specified SplitMix64 algorithm owned by this project; do not delegate reproducibility to an unspecified platform RNG implementation.

- [ ] **Step 4: Test bounds and ordering**

Prove map insertion order does not affect the digest, IDs are lexical UTF-8 order, the encoder fails with `SNAPSHOT_LIMIT_EXCEEDED` at its byte cap, and transcript commands are sorted by tick then sequence.

- [ ] **Step 5: Run all GL-free core checks**

Run: `./gradlew :gameplay-core:clean :gameplay-core:check --warning-mode=fail`

Expected: PASS.

- [ ] **Step 6: Commit replay proof**

```bash
git add gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/{replay,random} \
  gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/replay
git commit -m "feat: add deterministic replay digests"
```

## Milestone 3: libGDX, Runtime, and Box2D Adapters

### Task 7: Add fixed-step libGDX rendering and visual evidence

**Files:**

- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/visual/ScreenBounds.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/visual/WorldVisualEntry.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/visual/WorldVisualSnapshot.java`
- Create: `gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/visual/VisualEvidenceStatus.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/FixedStepLoop.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/AssetResolver.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/ResolvedFrame.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/AnimationSystem.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/GameplayRenderer.java`
- Create: `gameplay-libgdx/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/VisualSnapshotBuilder.java`
- Test: `gameplay-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/FixedStepLoopTest.java`
- Test: `gameplay-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/AssetResolverTest.java`
- Test: `gameplay-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/GameplayRendererTest.java`
- Test: `gameplay-libgdx/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/libgdx/VisualSnapshotBuilderTest.java`

- [ ] **Step 1: Write fixed-step and render-order tests first**

```java
@Test void capsCatchUpWithoutDroppingDeterministicTickOrder() {
    FixedStepLoop loop = new FixedStepLoop(Duration.ofSeconds(1).dividedBy(60), 5);
    assertEquals(5, loop.advance(Duration.ofMillis(100), world::step));
    assertEquals(5, world.tick());
    assertTrue(loop.hasBacklog());
}

@Test void drawOrderIsLayerThenOrderThenEntityId() {
    renderer.render(snapshotWithEntries("z", 1, 0, "a", 1, 0, "b", 0, 9));
    assertEquals(List.of("b", "a", "z"), recordingBatch.entityIds());
}
```

- [ ] **Step 2: Run adapter tests and observe failure**

Run: `./gradlew :gameplay-libgdx:test`

Expected: compilation FAIL because the visual and adapter APIs do not exist.

- [ ] **Step 3: Implement the fixed-step driver**

Use `System.nanoTime()` only to determine how many fixed ticks are due; the simulation itself receives only the configured fixed duration. The fixture runs at 60 ticks per second, clamps a render delta to 250 ms, executes at most five catch-up ticks per rendered frame, and retains the bounded remainder. Return a typed `FRAME_BACKLOG_EXCEEDED` failure if retained time would exceed the bound instead of silently discarding time.

- [ ] **Step 4: Implement caller-owned rendering**

```java
public final class GameplayRenderer {
    public GameplayRenderer(SpriteBatch batch, OrthographicCamera camera, AssetResolver assets);
    public void render(WorldSnapshot snapshot);
}

public final class AssetResolver {
    public AssetResolver(TextureAtlas atlas);
    public ResolvedFrame resolve(Sprite sprite, Animation animation, long tick);
}
```

The adapter never creates, replaces, or disposes the application's camera, batch, or atlas. Resolve every atlas region before drawing, fail with entity/component/asset location when missing, use nearest filtering for the fixture, and derive animation frame solely from simulation ticks. Render order is `(layer, order, entityId)`.

- [ ] **Step 5: Implement post-render visual evidence**

`VisualSnapshotBuilder` runs in `RENDER_PREP` after transforms and animation are final. It projects world bounds through the application camera, intersects viewport bounds, and emits up to 10,000 immutable entries containing entity ID, sprite reference, world bounds, screen bounds, visibility, draw order, and evidence status. A missing asset or unprojectable bound is a typed located status, never an omitted entity.

- [ ] **Step 6: Run GL tests under Xvfb**

Run: `xvfb-run -a ./gradlew :gameplay-libgdx:clean :gameplay-libgdx:check --warning-mode=fail`

Expected: PASS.

- [ ] **Step 7: Commit the libGDX adapter**

```bash
git add gameplay-core/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/core/visual \
  gameplay-libgdx/src/main gameplay-libgdx/src/test
git commit -m "feat: add fixed-step rendering adapter"
```

### Task 8: Add runtime projections and frame-correlated capture

**Files:**

- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeProjection.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeProjectionRegistry.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/StandardRuntimeProjections.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/GameplayRuntimeFrame.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/GameplayRuntimeBridge.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeOpenFrameSystem.java`
- Create: `gameplay-runtime/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeCaptureSystem.java`
- Test: `gameplay-runtime/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/GameplayRuntimeBridgeTest.java`
- Test: `gameplay-runtime/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeCorrelationTest.java`
- Test: `gameplay-runtime/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/runtime/RuntimeProjectionLimitsTest.java`

- [ ] **Step 1: Write bridge tests first**

```java
@Test void projectsDomainAndVisualValuesUnderOneFrameToken() {
    bridge.capture(runtimeFrameWithPlayerAndVisuals());
    RuntimeEntitySnapshot player = runtime.entity("gameplay.player").orElseThrow();
    assertEquals("PLAYING", player.value("screen"));
    assertEquals(frameToken, player.frameToken());
    assertEquals(frameToken, runtime.entity("gameplay.visual.player").orElseThrow().frameToken());
}

@Test void missingVisualEvidenceIsExplicit() {
    bridge.capture(runtimeFrameWithoutVisual("enemy-1"));
    assertEquals("UNAVAILABLE",
        runtime.entity("gameplay.visual.enemy-1").orElseThrow().value("status"));
}
```

- [ ] **Step 2: Run runtime tests and observe failure**

Run: `./gradlew :gameplay-runtime:test`

Expected: compilation FAIL because bridge classes do not exist.

- [ ] **Step 3: Implement projections and registration**

```java
public interface RuntimeProjection<T extends Component> {
    ComponentType<T> componentType();
    Map<String, RuntimeValue> project(EntityView entity, T component);
}

public final class GameplayRuntimeBridge implements AutoCloseable {
    public GameplayRuntimeBridge(AgentRuntime runtime,
                                 RuntimeProjectionRegistry projections,
                                 GameplayLimits limits);
    public List<GameSystem> systems();
    public void capture(GameplayRuntimeFrame frame);
    @Override public void close();
}
```

`GameplayRuntimeFrame` contains the immutable current `WorldSnapshot`, completed tick events, `WorldVisualSnapshot`, and runtime frame token available inside `RUNTIME_CAPTURE`; it does not depend on the later-created `CompletedTick`. Register bounded dynamic sources once, then serve immutable captured-frame data. Use stable entity IDs `gameplay.entity.<id>` and `gameplay.visual.<id>`. Project standard component fields, lifecycle state, tick, visual bounds/status, commands consumed, and attributed events. Translate event attributes into runtime attributes and call `causeNextChange` before the corresponding projected value change so damage, death, score, and removal retain causal evidence. Cap entities, values, strings, events, and visual entries using the same central limits.

- [ ] **Step 4: Implement phase and correlation rules**

`RuntimeOpenFrameSystem` opens the runtime frame at `INPUT`. `RuntimeCaptureSystem` captures after `RENDER_PREP` in `RUNTIME_CAPTURE`, records one stable token shared by every value from that tick, and completes the runtime frame exactly once. The application later records a `UiFrameCorrelation` from that token to the rendered harness frame; no bridge fabricates correlation before rendering. A skipped phase yields a typed `RUNTIME_FRAME_INCOMPLETE` failure. Never read Scene2D actors or renderer state from a runtime/MCP thread.

- [ ] **Step 5: Prove source ownership and cleanup**

Test that duplicate installation fails, `close()` unregisters only bridge-owned sources, a capture snapshot remains immutable while the next frame runs, and caller-owned `AgentRuntime` is never closed by the bridge.

- [ ] **Step 6: Run runtime checks**

Run: `./gradlew :gameplay-runtime:clean :gameplay-runtime:check --warning-mode=fail`

Expected: PASS.

- [ ] **Step 7: Commit runtime integration**

```bash
git add gameplay-runtime/src/main gameplay-runtime/src/test
git commit -m "feat: add correlated gameplay runtime projections"
```

### Task 9: Add Box2D authority, ownership, and contact evidence

**Files:**

- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dUnitConversion.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dSolverSettings.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dBodyHandle.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dBodyFactory.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/GameplayBox2dBridge.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dPhysicsSystem.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dContactCollector.java`
- Create: `gameplay-box2d/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dNativeDisposal.java`
- Test: `gameplay-box2d/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/GameplayBox2dBridgeTest.java`
- Test: `gameplay-box2d/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dAuthorityTest.java`
- Test: `gameplay-box2d/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dContactCollectorTest.java`
- Test: `gameplay-box2d/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/box2d/Box2dDisposalTest.java`

- [ ] **Step 1: Write native-lifecycle tests first**

```java
@Test void bodyTransformIsAuthoritativeAfterPhysics() {
    bridge.createBody(entityWithTransform(2, 3));
    bridge.apply(new MoveCommand(playerId, new Vec2(1, 0)));
    physicsSystem.run(contextAtTick(1));
    assertEquals(bridge.bodyPosition(playerId), context.transform(playerId).position());
}

@Test void disposalOccursAfterRuntimeCapture() {
    world.despawn(playerId);
    world.step();
    assertEquals(List.of("logical-despawn", "runtime-capture", "destroy-body"), observer.calls());
}
```

- [ ] **Step 2: Run Box2D tests and observe failure**

Run: `xvfb-run -a ./gradlew :gameplay-box2d:test`

Expected: compilation FAIL because the Box2D bridge does not exist.

- [ ] **Step 3: Implement explicit ownership and conversion**

```java
public final class GameplayBox2dBridge implements LifecycleParticipant, AutoCloseable {
    public GameplayBox2dBridge(World world,
                               Box2dBodyFactory bodies,
                               Box2dUnitConversion units,
                               Box2dSolverSettings solver,
                               AgentRuntime runtime,
                               GameplayLimits limits);
    public List<GameSystem> systems();
    @Override public void close();
}
```

The application owns and disposes `World`; the bridge owns only bodies/fixtures it creates. `Box2dUnitConversion` and velocity/position iteration counts are immutable and explicit (fixture uses 32 pixels per meter). Body user data stores the stable entity ID, never an entity object. Dynamic body position/rotation is copied Box2D → `Transform2D` after each step; commands apply velocity/impulses through the body and never overwrite transforms directly. Accept an explicit contact-listener composition point and never replace an application's existing listener silently.

- [ ] **Step 4: Implement bounded contact collection and inspection**

Collect begin/end contacts during the native callback into a preallocated bounded queue; sort drained records by fixture IDs before emitting `CollisionStarted`/`CollisionEnded` after `World.step`. Register the caller-owned world, bodies, fixtures, and drained contacts with `Box2dInspection`, using the runtime library's default adapter limits. Overflow is `BOX2D_CONTACT_LIMIT_EXCEEDED`, not truncation.

- [ ] **Step 5: Implement the disposal barrier**

Logical despawn hides the entity before `RENDER_PREP`; runtime capture still reports its terminal event; only the post-`RUNTIME_CAPTURE` barrier destroys fixtures/bodies and unregisters inspection entries. Reset and `close()` use the same ordered barrier and are idempotent.

- [ ] **Step 6: Run native and dependency checks**

Run: `xvfb-run -a ./gradlew :gameplay-box2d:clean :gameplay-box2d:check --warning-mode=fail`

Expected: PASS without native leaks or warnings.

- [ ] **Step 7: Commit Box2D integration**

```bash
git add gameplay-box2d/src/main gameplay-box2d/src/test
git commit -m "feat: add authoritative Box2D gameplay bridge"
```

## Milestone 4: Canonical Arena and Running-Game Evidence

### Task 10: Build the original arena domain, prefabs, and assets

**Files:**

- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaGameState.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaWorldFactory.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaInputProcessor.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/system/ArenaInputSystem.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/system/EnemyPursuitSystem.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/system/WeaponSystem.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/system/DamageSystem.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/system/DeathAndScoreSystem.java`
- Create: `gameplay-fixture/src/main/resources/gameplay/arena-prefabs.json`
- Create: `gameplay-fixture/src/main/resources/art/source/arena-floor.png`
- Create: `gameplay-fixture/src/main/resources/art/source/player.png`
- Create: `gameplay-fixture/src/main/resources/art/source/enemy.png`
- Create: `gameplay-fixture/src/main/resources/art/source/projectile.png`
- Create: `gameplay-fixture/src/main/resources/art/arena.atlas`
- Create: `gameplay-fixture/src/main/resources/art/arena.png`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaInputProcessorTest.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaCombatTest.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaPrefabTest.java`

- [ ] **Step 1: Write production-input and combat tests first**

Dispatch actual key-down/key-up pairs through the application-owned `InputMultiplexer`. Do not call systems or listener methods directly.

```java
@Test void aWPressProducesAtLeastOneTickOfUpwardIntent() {
    multiplexer.keyDown(Input.Keys.W);
    multiplexer.keyUp(Input.Keys.W);
    world.step();
    assertTrue(world.snapshot().component(playerId, Transform2D.TYPE).position().y() > startY);
}

@Test void threeProjectileContactsKillEnemyAndAwardScore() {
    fireThreeProductionPressesAtEnemy();
    advanceUntil(() -> world.entity(enemyId).isEmpty(), 600);
    assertEquals(300, state.score());
    assertEquals(3, eventsOfType(DamageApplied.class).size());
    assertEquals(1, eventsOfType(EntityKilled.class).size());
}
```

- [ ] **Step 2: Run fixture tests and observe failure**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:test --tests '*ArenaInputProcessorTest' --tests '*ArenaCombatTest'`

Expected: compilation FAIL because the arena implementation does not exist.

- [ ] **Step 3: Implement the production command path**

`ArenaInputProcessor` maps WASD state and Space presses to `MoveCommand`, `AimCommand`, and `FireCommand` envelopes for the next fixed tick. Key-down followed immediately by key-up retains one tick of intent. Held state persists only until the genuine key-up. Source ID is `keyboard`; sequences are monotonic and reset only with the world.

- [ ] **Step 4: Implement the deterministic arena loop**

Use a 960 × 540 viewport with a persistent 80-pixel HUD strip and a gameplay-safe rectangle of `(24, 24)` through `(936, 436)`. Use 32 pixels per Box2D meter. Player and enemy visuals are 32 × 32 pixels with centered origins and 28 × 28 centered colliders; projectiles are 12 × 12 with 8 × 8 colliders. The enemy starts at `(760, 220)`, player at `(180, 220)`, and the fixed aim direction is the current player-to-enemy vector. Enemy pursuit, firing cooldown, projectile lifetime, contact damage, three-hit death, score, and reset are simulation-tick state only.

- [ ] **Step 5: Define strict prefabs**

`arena-prefabs.json` contains exactly `player`, `enemy`, `projectile`, and `wall` definitions using the standard component IDs. Load and compile it once in `ArenaWorldFactory`; tests assert every sprite/animation reference resolves and every collider stays within its declared visual bounds.

- [ ] **Step 6: Generate and pack original bitmap art**

Invoke the `imagegen` skill during execution for four original, orthographic, flat-color source sheets: a seamless arena-floor tile, a transparent player strip, a transparent enemy animation strip, and a transparent projectile. Use no text and crisp silhouettes. Normalize character/projectile frames into 32 × 32 or 12 × 12 cells matching the dimensions above, pack them into one power-of-two `arena.png`, and write a deterministic `arena.atlas` with regions `arena-floor`, `player-idle`, `player-hit`, `enemy-idle`, `enemy-hit`, `enemy-death-0..3`, and `projectile`. Inspect each source and the packed atlas at original resolution before accepting it. Record prompts and normalization in the evidence document; do not use third-party copyrighted assets.

- [ ] **Step 7: Run arena domain tests**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:clean :gameplay-fixture:test --warning-mode=fail`

Expected: PASS.

- [ ] **Step 8: Commit the playable domain**

```bash
git add gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/{ArenaGameState.java,ArenaWorldFactory.java,ArenaInputProcessor.java,system} \
  gameplay-fixture/src/main/resources/gameplay \
  gameplay-fixture/src/main/resources/art \
  gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture
git commit -m "feat: add canonical arena gameplay loop"
```

### Task 11: Add markup-only HUD, harness session, and stdio MCP launcher

**Files:**

- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaApplication.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/Launcher.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaHarness.java`
- Create: `gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaRuntimeProjection.java`
- Create: `gameplay-fixture/src/main/resources/ui/hud.xml`
- Create: `gameplay-fixture/src/main/resources/ui/hud.gdxcss`
- Test: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaMarkupTest.java`
- Test: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaApplicationTest.java`

- [ ] **Step 1: Write semantic markup tests first**

```java
@Test void markupBuildsTheEntireHudWithStableSemantics() {
    Snapshot snapshot = launchAndSnapshotTitle();
    assertOne(snapshot, role("button").and(accessibleName("Start game")));
    assertOne(snapshot, testId("score-value"));
    assertOne(snapshot, testId("health-value"));
    assertOne(snapshot, role("button").and(accessibleName("Reset arena")));
}

@Test void startUsesRealScene2dInputAndDoesNotExit() {
    harness.press(role("button").and(accessibleName("Start game")), Input.Keys.ENTER);
    harness.waitFor(text("PLAYING"));
    assertFalse(application.shouldExit());
}
```

- [ ] **Step 2: Run markup tests and observe failure**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:test --tests '*ArenaMarkupTest' --tests '*ArenaApplicationTest'`

Expected: compilation FAIL or resource lookup FAIL because the application and markup do not exist.

- [ ] **Step 3: Author the complete UI in XML/GDXCSS**

`hud.xml` owns the title overlay, Start game button, persistent screen/health/score labels, help text, game-over message, and Reset arena button. Use canonical `.gdxcss`, Table/Cell attributes for layout, and semantic attributes by construction. Bind these labels to independent domain entities:

```xml
<label id="screen-value" data-runtime-entity="gameplay.arena" data-runtime-property="screen" />
<label id="health-value" data-runtime-entity="gameplay.player" data-runtime-property="health.current" />
<label id="score-value" data-runtime-entity="gameplay.arena" data-runtime-property="score" />
```

No Java code creates, rearranges, or semantically binds HUD actors. Java supplies data/actions to the markup runtime only.

- [ ] **Step 4: Wire application-owned libGDX objects on the render thread**

`ArenaApplication` creates and owns one `Stage`, `InputMultiplexer`, `World`, camera, batch, atlas, `AgentRuntime`, gameplay world, and render loop. `MarkupBuilder.build`, `HarnessSemanticSink`, `Scene2dSession`, and `MarkupRuntimeSource.registerBindings` all run on the render thread. Dispose in reverse ownership order; adapters do not dispose application-owned objects.

- [ ] **Step 5: Wire the fixed harness session**

Publish session ID `arena` before MCP requests. Bind the session to the application's Stage and input multiplexer, a render-thread scheduler, monotonic revision/frame suppliers, and a frame fence advanced on every rendered frame including title, game-over, and paused frames. Route the `WaitEngine` snapshot supplier through `scheduler.submit(...).join()`. Use a `FileArtifactStore` rooted at `build/artifacts` with existing harness defaults and close it with the MCP session.

- [ ] **Step 6: Wire runtime correlation and stdio launch modes**

`ArenaRuntimeProjection` is the independent authority for `gameplay.arena`, player health, enemy health, score, visual evidence, and attributed collision/damage/death events. `MarkupRuntimeSource.registerBindings` installs only the XML correlations. Support `--mcp`, `--smoke N --screenshot PATH`, and normal interactive launch. `--mcp` serves only `HarnessMcpServer` over `System.in`/`System.out`; do not co-serve `RuntimeMcpServer` or open a network socket.

After gameplay values and markup text are rendered, record one `UiFrameCorrelation` from the latest runtime token to the actual harness frame/revision on every frame. Add an integration test that deliberately supplies one wrong rendered score on the render thread while the domain remains unchanged and proves `ui_runtime_compare` returns `MISMATCH`; restore the correct supplier and prove `EQUAL`. This is test-only setup, not a production control or alternate gameplay path.

- [ ] **Step 7: Run application tests and smoke**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:check --warning-mode=fail`

Expected: PASS.

Run: `xvfb-run -a ./gradlew :gameplay-fixture:run --args='--smoke 180 --screenshot build/smoke.png'`

Expected: exit 0 and a 960 × 540 title screenshot at `build/smoke.png`.

- [ ] **Step 8: Open and inspect the smoke screenshot**

Use the image viewer at original resolution. Verify the title, complete HUD, arena bounds, no clipping, no missing texture, and no gameplay entity drawn over the HUD strip. A successful process exit alone is insufficient.

- [ ] **Step 9: Commit the running fixture**

```bash
git add gameplay-fixture/src/main/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/{ArenaApplication.java,Launcher.java,ArenaHarness.java,ArenaRuntimeProjection.java} \
  gameplay-fixture/src/main/resources/ui \
  gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/{ArenaMarkupTest.java,ArenaApplicationTest.java}
git commit -m "feat: wire markup harness and runtime fixture"
```

### Task 12: Add deterministic, runtime, and Box2D fixture qualification

**Files:**

- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaDeterminismTest.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaRuntimeTest.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaBox2dTest.java`
- Create: `gameplay-fixture/src/test/resources/transcripts/arena-three-hit.json`

- [ ] **Step 1: Write cross-layer qualification tests**

```java
@Test void resetAndReplayMatchEveryTickAndEventDigest() {
    TranscriptResult first = fixture.run(transcript("arena-three-hit.json"));
    fixture.reset();
    TranscriptResult second = fixture.run(transcript("arena-three-hit.json"));
    assertEquals(first.tickDigests(), second.tickDigests());
    assertEquals(first.eventDigests(), second.eventDigests());
}

@Test void damageIsAttributedToTheObservedContact() {
    fixture.runUntilFirstHit();
    assertRuntime("gameplay.player", "health.current", 2L);
    assertRuntimeEvent("DamageApplied", Map.of("contact", firstContactId, "amount", 1L));
    assertBox2dFixtureOverlapsVisual("player");
}
```

- [ ] **Step 2: Run the new tests and observe the missing evidence**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:test --tests '*ArenaDeterminismTest' --tests '*ArenaRuntimeTest' --tests '*ArenaBox2dTest'`

Expected: tests FAIL at the first not-yet-exposed digest, attribution, or geometry assertion.

- [ ] **Step 3: Add only the missing public evidence**

Expose immutable completed-tick/event digests through the fixture test driver, project the stable Box2D contact ID into `DamageApplied`, and expose visual/collider bounds through their existing runtime projections. Do not add visible automation controls, alternate controllers, teleports, invulnerability, time skips, or direct state mutation.

- [ ] **Step 4: Run the cross-layer rung twice**

Run twice: `xvfb-run -a ./gradlew :gameplay-fixture:clean :gameplay-fixture:test --tests '*ArenaDeterminismTest' --tests '*ArenaRuntimeTest' --tests '*ArenaBox2dTest' --warning-mode=fail`

Expected: PASS both times with the same digest series.

- [ ] **Step 5: Commit qualification tests**

```bash
git add gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/{ArenaDeterminismTest.java,ArenaRuntimeTest.java,ArenaBox2dTest.java} \
  gameplay-fixture/src/test/resources/transcripts
git commit -m "test: qualify deterministic arena integrations"
```

### Task 13: Prove the real app through stdio MCP and complete first-playable evidence

**Files:**

- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/HarnessMcpBlackBoxTest.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaMcpClient.java`
- Create: `gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/ArenaProcess.java`
- Create: `docs/evidence/first-playable.md`
- Create: `docs/evidence/screenshots/01-actionable.png`
- Create: `docs/evidence/screenshots/02-movement-fire.png`
- Create: `docs/evidence/screenshots/03-enemy-death.png`
- Create: `docs/evidence/screenshots/04-reset.png`

- [ ] **Step 1: Write the black-box MCP test first**

Launch the built fixture fat JAR as `java -jar gameplay-fixture/build/libs/libgdx-agent-gameplay-fixture.jar --mcp` under Xvfb. `ArenaMcpClient` is a minimal synchronous JSON-RPC client modeled on the harness repository's proven process fixture: it performs the real MCP initialize/initialized handshake, asserts the production server identity and 23-tool catalog, then calls tools over the child process's stdin/stdout. The test must call the real catalog, not `HarnessProtocolService` directly:

```text
ui_sessions
ui_query(Start game)
ui_action(Press Enter)
ui_wait(screen = PLAYING)
ui_assert(health = 3, score = 0)
ui_runtime_compare(gameplay.arena.screen = screen-value)
ui_screenshot
```

Then dispatch real production W/D/Space presses through `ui_action`, wait for movement/fire and three damage events, assert enemy removal and score 300, retrieve screenshots, reset through the visible Reset arena button, and assert the original correlated values. Close stdin and assert clean process exit and artifact cleanup.

- [ ] **Step 2: Run the black-box test and observe failure**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:test --tests '*HarnessMcpBlackBoxTest' --warning-mode=fail`

Expected: FAIL at the first missing process, MCP, gesture, correlation, or artifact behavior.

- [ ] **Step 3: Fix only production-path failures**

Correct launcher/session/input/runtime wiring exposed by the black-box run. If harness 1.2.1 cannot express a required gesture, document that finding and extend the library capability in a separately reviewed dependency change; do not deform fixture controls. Space remains a genuine key-down/key-up fire press.

- [ ] **Step 4: Run broad layout checks in the actionable state**

Call `ui_validate_layout` for the full session and every available broad check described by the harness guide. Record each result. Any `CHECK_UNAVAILABLE`, overlap, clipping, unreachable control, or out-of-bounds finding remains a failure until corrected or explicitly scoped by a typed supported contract.

- [ ] **Step 5: Capture and visually inspect four original-size screenshots**

Retrieve opaque screenshot receipts through MCP. Before session close, the local qualification runner locates the session-owned artifact by matching the receipt's SHA-256, byte length, and PNG media type inside the bounded `FileArtifactStore`; the MCP response and public API never reveal a filesystem path. Copy only those verified PNG bytes into `docs/evidence/screenshots`, then close the session and assert the store is empty. Open each at 960 × 540 original resolution. Inspect actionable state, movement/fire response, enemy death/score, and deterministic reset. Record concrete observations about visual/collider alignment, HUD persistence, safe region, legibility, animation state, and any subjective feel risk.

- [ ] **Step 6: Complete the first-playable template**

Copy `docs/templates/first-playable-evidence.md` from the bootstrap as `docs/evidence/first-playable.md`, then replace every prompt with observed evidence: mechanic source/design intent, authored versus simulated degrees of freedom, production inputs, fixed-step boundary, visual/collision dimensions and pivots, 32 px/m mapping, safe gameplay region, HUD regions, MCP call results, runtime equality and deliberate mismatch result, screenshot inspection, walkthrough observations, and remaining subjective risks. Do not leave unchecked boxes or placeholder language.

- [ ] **Step 7: Run the full fixture evidence rung**

Run: `xvfb-run -a ./gradlew :gameplay-fixture:clean :gameplay-fixture:check --warning-mode=fail`

Expected: PASS, including the real stdio MCP black-box test.

- [ ] **Step 8: Commit reviewed evidence**

```bash
git add gameplay-fixture/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/fixture/{HarnessMcpBlackBoxTest.java,ArenaMcpClient.java,ArenaProcess.java} \
  docs/evidence
git commit -m "test: prove the arena through real MCP"
```

## Milestone 5: Publication Readiness and Public Repository

### Task 14: Add Maven-local qualification, API compatibility, documentation, and manual release workflows

**Files:**

- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `gameplay-core/build.gradle.kts`
- Modify: `gameplay-libgdx/build.gradle.kts`
- Modify: `gameplay-runtime/build.gradle.kts`
- Modify: `gameplay-box2d/build.gradle.kts`
- Create: `docs/guides/getting-started.md`
- Create: `docs/guides/prefabs.md`
- Create: `docs/guides/libgdx-integration.md`
- Create: `docs/guides/runtime-evidence.md`
- Create: `docs/guides/box2d-integration.md`
- Create: `docs/guides/releasing.md`
- Create: `verification/consumer/settings.gradle.kts`
- Create: `verification/consumer/build.gradle.kts`
- Create: `verification/consumer/src/main/java/example/ConsumerSmoke.java`
- Create: `scripts/verify-maven-local.sh`
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/stage-maven-central.yml`
- Create: `.github/workflows/manage-maven-central.yml`
- Create: `.github/dependabot.yml`
- Test: `gameplay-core/src/test/java/io/github/teemuki8/libgdx/agent/gameplay/core/DocumentationContractTest.java`

- [ ] **Step 1: Write documentation/publication contract tests first**

```java
@Test void everyPublishedCoordinateAndGuideIsDeclared() throws Exception {
    assertPublishedModules(List.of(
        "gameplay-core", "gameplay-libgdx", "gameplay-runtime", "gameplay-box2d"));
    assertNotPublished("gameplay-fixture");
    assertGuideLinksResolve("README.md");
    assertNoPlaceholderLanguage("README.md", "docs/guides", "docs/evidence");
}

@Test void releaseWorkflowsCannotRunOnPush() throws Exception {
    assertTriggers("stage-maven-central.yml", Set.of("release.published", "workflow_dispatch"));
    assertTriggers("manage-maven-central.yml", Set.of("workflow_dispatch"));
}
```

- [ ] **Step 2: Run contract tests and observe failure**

Run: `./gradlew :gameplay-core:test --tests '*DocumentationContractTest'`

Expected: FAIL because guides and workflows do not exist.

- [ ] **Step 3: Configure publishable artifacts and POMs**

Apply `maven-publish` and `signing` only to the four library modules. Default version is `0.1.0-SNAPSHOT`; release builds require `-PreleaseVersion`. Every publication contains the binary, sources, Javadocs, `META-INF/LICENSE`, `META-INF/NOTICE`, name, description, project URL, Apache-2.0 license, SCM connection, developer name/email/URL, and no dynamic or project-only dependency versions. Sign only when publishing a non-SNAPSHOT release to the configured Central repository.

Add japicmp `0.23.1` as build tooling and one `apiCompatibility<Module>` task per published module. With no released baseline, the initial V1 task reports an explicit skip; once a baseline version property is supplied, binary/source incompatible changes fail the build.

- [ ] **Step 4: Add archive, POM, and consumer verification**

`verifyPublicationArchives` opens all four JAR/source/Javadoc artifacts and checks deterministic timestamps, license/notice, package presence, and the absence of fixture classes/assets. `verifyPublishedPoms` parses all generated POMs and asserts the exact coordinates and metadata. `scripts/verify-maven-local.sh` publishes `0.1.0-SNAPSHOT` to an isolated temporary Maven repository, then builds `verification/consumer` against all four coordinates and runs `ConsumerSmoke` without project dependencies.

- [ ] **Step 5: Write agent-first guides and executable examples**

README leads with the smallest GL-free world, then links strict prefab, libGDX ownership/render-thread, runtime projection/correlation, Box2D authority/disposal, fixture/MCP, and release guides. Every example compiles against the public API. Document hard defaults, phase/slot catalog, failure codes with corrective examples, lifecycle timing, frame correlation, coordinate conversion, artifact coordinates, Maven Central badges/links, and the exact no-release-without-authorization boundary.

- [ ] **Step 6: Add pinned CI**

Copy the proven workflow structure from the sibling teemuki8 stack and pin every third-party action to a full commit SHA. Pull requests and `main` pushes run:

```bash
./gradlew :gameplay-core:clean :gameplay-core:check --warning-mode=fail
xvfb-run -a ./gradlew :gameplay-libgdx:check :gameplay-runtime:check :gameplay-box2d:check --warning-mode=fail
xvfb-run -a ./gradlew :gameplay-fixture:check --warning-mode=fail
xvfb-run -a ./gradlew clean check javadoc apiCompatibility verifyPublicationArchives verifyPublishedPoms --warning-mode=fail
./scripts/verify-maven-local.sh
```

Upload only bounded test reports/evidence on failure. Enable dependency submission and Gradle wrapper validation. Use least-privilege `contents: read` by default.

- [ ] **Step 7: Add user-managed Central workflows without running them**

`stage-maven-central.yml` mirrors the sibling repos: trigger only on a published GitHub release or manual dispatch, require a semantic `vX.Y.Z` tag, validate Central/signing secrets, run the full Xvfb gate, sign/upload, and transfer with `publishing_type=user_managed`. `manage-maven-central.yml` is manual-only with `inspect`, `publish`, and `drop`; `publish` requires a validated deployment and exact PURLs for all four gameplay coordinates. Candidate inspection verifies the core JAR license/notice and POM developer metadata. Do not dispatch either workflow in this task.

- [ ] **Step 8: Refresh and audit dependency trust**

Run: `./scripts/refresh-verification-metadata.sh --no-daemon --console=plain`

Review: `git diff -- gradle/verification-metadata.xml gradle/dependency-locks '*.lockfile'`

Expected: only coordinates required by the declared build, tests, japicmp, and fixture. Remove unexplained candidates before committing; never enable automatic trust during normal builds or IDE sync.

- [ ] **Step 9: Run publication readiness locally**

Run: `xvfb-run -a ./gradlew clean check javadoc apiCompatibility verifyPublicationArchives verifyPublishedPoms --warning-mode=fail`

Expected: PASS.

Run: `./scripts/verify-maven-local.sh`

Expected: PASS against isolated Maven-local output.

- [ ] **Step 10: Commit the release-ready repository**

```bash
git add README.md AGENTS.md build.gradle.kts gradle gameplay-* docs/guides verification scripts \
  .github config LICENSE NOTICE
git commit -m "build: prepare gameplay libraries for publication"
```

### Task 15: Run the exact-head review, create the public GitHub repository, and verify remote parity

**Files:**

- Review: all tracked files and commits in the standalone repository
- Remote target: `https://github.com/teemuki8/libgdx-agent-gameplay`

- [ ] **Step 1: Prove local isolation and scope**

Run:

```bash
git status --short
git remote -v
git rev-parse --show-toplevel
git log --oneline --decorate --max-count=20
```

Expected: clean worktree; top level is this standalone directory; no remote points to `libgdx-agent-bootstrap`; commits contain only the approved library, fixture, evidence, docs, and build infrastructure.

- [ ] **Step 2: Review the exact head**

Use the `requesting-code-review` and `verification-before-completion` skills during execution. Review `git diff --check`, every public API, boundedness/thread/ownership rules, prefab strictness, deterministic ordering, runtime correlation, Box2D disposal, markup-only UI, real-input MCP evidence, POM/workflow safety, and license provenance. Resolve all actionable findings and rerun the affected rung before proceeding.

- [ ] **Step 3: Run the final clean gate from the reviewed head**

Run:

```bash
xvfb-run -a ./gradlew clean check javadoc apiCompatibility verifyPublicationArchives verifyPublishedPoms --warning-mode=fail
./scripts/verify-maven-local.sh
git diff --check
git status --short
```

Expected: every command PASS and worktree clean. Record the exact `git rev-parse HEAD` as the publication candidate.

- [ ] **Step 4: Confirm the target does not already conflict**

Run: `gh repo view teemuki8/libgdx-agent-gameplay --json nameWithOwner,isPrivate,defaultBranchRef,url`

Expected: repository not found. If it exists, stop and compare ownership/history before changing any remote state.

- [ ] **Step 5: Create and push the authorized public repository**

Run exactly from this standalone clean root:

```bash
gh repo create teemuki8/libgdx-agent-gameplay \
  --public \
  --description "Deterministic agent-ready gameplay architecture for libGDX" \
  --source=. \
  --remote=origin \
  --push
```

This public repository creation and initial `main` push are authorized by the approved design. Do not create a tag, GitHub release, Central deployment, or release-workflow dispatch.

- [ ] **Step 6: Verify hosted visibility and exact-head parity**

Run:

```bash
gh repo view teemuki8/libgdx-agent-gameplay --json nameWithOwner,isPrivate,defaultBranchRef,url
git ls-remote --exit-code origin refs/heads/main
git rev-parse HEAD
```

Expected: `isPrivate` is `false`, default branch is `main`, and remote `refs/heads/main` exactly equals the reviewed local head.

- [ ] **Step 7: Verify hosted CI at the same head**

Inspect the Actions run for the pushed commit with `gh run list --commit <exact-head>` and `gh run watch <run-id> --exit-status`. If CI fails, use the `github:gh-fix-ci` and `systematic-debugging` skills: inspect the failing logs, reproduce locally, add a failing regression when behavior changed, apply the narrow fix, rerun the full gate, review the new exact head, push, and verify the replacement run.

- [ ] **Step 8: Perform the no-release audit and handoff**

Verify:

```bash
git tag --list
gh release list --repo teemuki8/libgdx-agent-gameplay
gh run list --workflow 'Stage Maven Central' --repo teemuki8/libgdx-agent-gameplay
```

Expected: no version tag, no GitHub release, and no Stage Maven Central run. Report the public URL, exact main SHA, CI URL/result, local verification commands, four future Maven coordinates, and that release/publication remains pending separate authorization.

## Acceptance-Criteria Traceability

| Design criterion | Implemented and proved by |
| --- | --- |
| 1. Four artifacts compile with one-way dependencies | Tasks 1 and 14 module graph, independent archives, and isolated consumer |
| 2. Strict prefab-authored fixture entities | Task 5 closed parser/schema tests; Task 10 four-prefab arena catalog |
| 3. Stable inspectable schedule | Task 4 ordering and ambiguity tests |
| 4. Documented lifecycle barriers and reset | Task 4 barrier tests; Tasks 9 and 12 native cleanup/reset proof |
| 5. Production input creates ordered fixed-tick commands | Tasks 3 and 10 multiplexer key tests; Task 13 MCP presses |
| 6. Single Box2D authority and bridge-only disposal | Task 9 authority/contact/disposal tests; Task 12 fixture evidence |
| 7. Rendering/animation never become authority | Task 7 fixed-state adapter tests; Task 12 replay equivalence |
| 8. Automatic bounded correlated runtime projection | Task 8 bridge tests; Tasks 11–13 equality, attribution, and correlation proof |
| 9. Visual semantics and collider alignment | Task 7 visual snapshots; Tasks 12–13 runtime geometry and inspected images |
| 10. Complete arena loop | Tasks 10–13 movement, enemy, projectile, collision, damage, death, HUD, and reset walkthrough |
| 11. Same transcript produces matching per-tick digests | Tasks 6 and 12 repeated state/event digest runs |
| 12. Markup constructs all Scene2D UI and real MCP drives it | Tasks 11 and 13 semantic XML/GDXCSS plus stdio black-box calls |
| 13. Runtime/UI/layout/walkthrough/evidence gates | Tasks 11–13 EQUAL/MISMATCH, layout results, screenshots, and completed template |
| 14. Local Maven readiness without staging | Task 14 Javadocs, archives, POMs, signatures, consumer, and API gate |
| 15. Clean public GitHub main with CI/head parity | Task 15 exact-head review, public push, CI parity, and no-release audit |
