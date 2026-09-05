# libGDX integration

The application owns the `OrthographicCamera`, `SpriteBatch`, `TextureAtlas`, viewport, Stage, and
render loop. `GameplayRenderer` borrows camera, batch, and `AssetResolver`; closing it only clears
adapter state. Dispose application resources after closing gameplay adapters.

Construct and use `Stage`, Actors, `GameplayRenderer`, atlas regions, and visual snapshot projection
on the libGDX render thread. GL-free parsing and immutable snapshots may run elsewhere. Rendering
consumes a completed `WorldSnapshot`; it never advances animation, physics, commands, or gameplay.

`Render.layer` is a semantic identifier sorted lexically, followed by explicit numeric order and
entity ID. Choose layer names whose lexical order matches the intended painter order, for example
`background`, `characters`, `projectiles`. Asset lookup failure is the typed `MISSING_ASSET`
diagnostic rather than a fallback texture.

`FixedStepLoop` accepts measured render duration, caps one delta and retained backlog at 250 ms,
and executes only exact fixed ticks with a bounded catch-up count. Its interpolation alpha is
presentation-only and must never flow back into authoritative state.

For markup/harness projects, continue constructing every Scene2D UI from XML/GDXCSS. The gameplay
renderer draws world sprites; markup owns the HUD actor tree and its semantic/runtime declarations.
Advance the harness frame fence after every rendered frame, including paused/title frames.

## Explicit viewport and interpolation (unreleased additive APIs)

Copy the actual **physical framebuffer** rectangle into `RenderView(framebufferWidth,
framebufferHeight, viewportX, viewportY, viewportWidth, viewportHeight)`. Viewport origin is
bottom-left, while visual evidence and input use top-left pixels. Pass the same view to
`GameplayRenderer.render(presentation, view)`, `VisualSnapshotBuilder(camera, assets, view,
maxEntries, unitConversion)`, and `RenderViewCoordinates.worldPosition(camera, view, input)`.
The renderer temporarily applies and restores GL viewport state. Input mapping excludes letterbox
bars; convert logical window input to physical pixels first. A cropped oversized viewport is valid
when it intersects the framebuffer. Existing framebuffer-only overloads retain full-frame behavior.

`PresentationFrame.between(previous, current, alpha)` uses the fixed-step loop's alpha for position
and shortest-arc rotation, bounded by the gameplay entity maximum. Use consecutive completed ticks
only, and `PresentationFrame.current(current)` after reset, teleport or same-ID replacement. New
entities use current poses and removed entities remain absent; size, pivot, animation and visibility
come from the current snapshot. This is optional render interpolation, not prediction or authority.
Pass the same presentation frame to rendering and visual evidence. Collider evidence deliberately
stays at the authoritative current pose, so interpolation-related visual/collider offsets remain
observable. Never write presentation poses into components, physics, commands or runtime domain
projections. These adapters do not replace markup/HUD construction or change the canonical arena art.
