# Runtime evidence

`GameplayRuntimeBridge` projects immutable completed gameplay into an application-owned
`AgentRuntime`. Install the bridge and any application-specific entity registrations before
`runtime.start()`, add its systems to the world, prepare visual evidence in `RENDER_PREP`, and let
the bridge complete capture in `RUNTIME_CAPTURE`.

Standard runtime IDs are deterministic: `gameplay.frame`, `gameplay.entity.<entity-id>`, and
`gameplay.visual.<entity-id>`. Properties include the fixed tick/frame token, lifecycle,
component projections, asset/region, world and screen bounds, pivot, visibility, camera visibility,
render layer, and render order. Damage/death changes retain event attribution when the event maps
unambiguously to one property change.

The bridge owns only its dynamic runtime source registration. The application owns and closes the
runtime. Close application-specific registrations, the gameplay bridge, then the runtime, all on
the capture/owner thread.

When markup displays runtime state, register bindings with `MarkupRuntimeSource` and the same
stable correlation token used by `HarnessSemanticSink`. After rendering, record exactly one
`UiFrameCorrelation` that maps the latest completed runtime frame to that UI frame. If no fixed
simulation tick ran, capture a presentation-only runtime frame without advancing physics so the UI
frame still has a unique proof. Missing or drifted evidence must remain `UNCORRELATED`, `STALE`, or
`UNAVAILABLE`; never substitute a widget read as domain authority.
