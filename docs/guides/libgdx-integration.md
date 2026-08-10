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
