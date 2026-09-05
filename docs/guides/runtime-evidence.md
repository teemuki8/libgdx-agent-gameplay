# Runtime evidence

`GameplayRuntimeBridge` projects immutable completed gameplay into an application-owned
`AgentRuntime`. Install the bridge and any application-specific entity registrations before
`runtime.start()`, add its systems to the world, prepare visual evidence in `RENDER_PREP`, and let
the bridge complete capture in `RUNTIME_CAPTURE`.

Standard runtime IDs are deterministic: `gameplay.frame`, `gameplay.entity.<entity-id>`, and
`gameplay.visual.<entity-id>`. Properties include the fixed tick/frame token, lifecycle,
component projections, asset/region, world and screen bounds, pivot, visibility, camera visibility,
render layer/order, copied collider bounds, the declared unit conversion, and visual/collider
center-alignment delta. Missing collider evidence is a typed null value rather than inferred native
state. Custom state becomes runtime authority only through an explicit `RuntimeProjection` over a
component already copied into the completed world snapshot. Damage/death changes retain event
attribution when the event maps unambiguously to one property change.

The bridge owns only its dynamic runtime source registration. The application owns and closes the
runtime. Close application-specific registrations, the gameplay bridge, then the runtime, all on
the capture/owner thread.

When markup displays runtime state, register bindings with `MarkupRuntimeSource` and the same
stable correlation token used by `HarnessSemanticSink`. After rendering, record exactly one
`UiFrameCorrelation` that maps the latest completed runtime frame to that UI frame. If no fixed
simulation tick ran, capture a presentation-only runtime frame without advancing physics so the UI
frame still has a unique proof. Missing or drifted evidence must remain `UNCORRELATED`, `STALE`, or
`UNAVAILABLE`; never substitute a widget read as domain authority.

## Application event codecs and capacity (unreleased additive APIs)

Build one immutable `EventCodecRegistry` with explicit stable IDs, exact application event classes,
and deterministic copying functions returning `Payload(subject, source, EventAttributes)`. Share
that registry with `CanonicalWorldEncoder(maxBytes, components, codecs)` and
`GameplayRuntimeBridge(runtime, projections, limits, codecs)`. Canonical digests include custom
type, endpoints and sorted typed payload fields; runtime emits `gameplay.<registered-id>` with the
same evidence. The default overloads still reject unknown events and standard event bytes remain
unchanged. No class loading or reflective field discovery occurs. At most 256 custom codecs are
allowed; IDs are unique, at most 128 characters, and cannot override standard types/classes.
Payload plus envelope has at most 32 distinct fields; duplicate field names reject instead of
silently replacing evidence. Codecs must copy every meaningful custom value and must not consult
mutable external state. Register the same schema wherever transcripts are replayed.

Before the first world tick call `bridge.validateCapacity(maximumGameplayEntities,
reservedOtherRuntimeEntities)`. It validates `1 + 2 * maximum + reserved` against the **actual**
runtime configuration, not a guessed default: each gameplay entity has domain and visual evidence,
plus one shared frame entity. The application declares its intended bound and every other source's
reserve. A later gameplay population above that bound fails. This preflight does not predict custom
property/event/string/staging volume; opted-in captures additionally reject frame diagnostics and
truncation evidence across frames, entities, events and decisions (including incomplete decisions)
instead of publishing a new successful frame token. Inspect retained runtime evidence for the cause,
then reduce volume or configure measured explicit bounds; never silently truncate authority. Legacy
constructors without preflight preserve their prior behavior. A failed tick is not rollback: the
application remains responsible for world recovery and runtime lifecycle.
