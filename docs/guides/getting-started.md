# Getting started

Use `gameplay-core` first and add adapter modules only when the application owns their required
resources. All public coordinates use group `io.github.teemuki8`; replace `VERSION` with one exact
released version rather than a range or dynamic selector.

```kotlin
dependencies {
    implementation("io.github.teemuki8:gameplay-core:VERSION")
    implementation("io.github.teemuki8:gameplay-libgdx:VERSION")
    implementation("io.github.teemuki8:gameplay-runtime:VERSION")
    implementation("io.github.teemuki8:gameplay-box2d:VERSION")
}
```

Create, mutate, step, reset, and close a `GameWorld` on its owner thread. Snapshots are immutable
and may be read elsewhere. Entity drafts activate at the next tick barrier; despawns become logical
removal before native disposal. Never retain an `EntityView` across a tick.

The stable phase order is `INPUT`, `PRE_PHYSICS`, `PHYSICS`, `POST_PHYSICS`, `GAMEPLAY`,
`ANIMATION`, `RENDER_PREP`, `RUNTIME_CAPTURE`. A `SystemDescriptor` supplies a unique ID, phase,
and explicit slot; duplicate phase/slot pairs fail during schedule compilation.

V1 hard maxima are 10,000 entities, 64 components per entity, 256 systems, 4,096 queued commands,
4,096 pending mutations, 4,096 events per tick, 10,000 visual entries, and a 4 MiB canonical
snapshot. Applications may lower these values but cannot raise them.

Failures are `GameplayException` values with a stable `GameplayDiagnosticCode`, operation,
expected value, observed value, and corrective action. For example, `LATE_COMMAND` means the
target tick has already passed; enqueue for the current/future tick instead of retrying an obsolete
envelope. `OWNER_THREAD_VIOLATION` means mutation must be scheduled back to the world owner.

For a complete integration, inspect the non-published `gameplay-fixture`: it composes a real fixed
tick world, Box2D, rendering, markup-only HUD, runtime evidence, and stdio harness without changing
the library ownership boundaries.
