# Presentation visual refresh

Continuation of the approved adaptable-example program. Dispatch resize while paused
exposed stale bridge screen bounds: `prepareVisuals` only accepts an open simulation tick.

Add `refreshPresentationVisuals(WorldVisualSnapshot)` to the existing bridge. It accepts
only the last successfully completed world tick, rejects unknown/duplicate entities and
oversized evidence, and runs on the owner thread between ticks. It changes only cached
visual evidence. It never advances the world, starts/ends a runtime frame, emits events,
changes domain projections, or records a UI correlation. The application then captures its
ordinary presentation runtime frame and correlates it with the completed UI frame.

Alternatives: advancing a paused tick breaks pause semantics; a second app-owned runtime
visual store would duplicate bridge authority. Keep one bridge and an explicit refresh.

Verification: failing bridge regressions first; 50 paused presentation refreshes with
unchanged world/tick/domain values and no repeated events; invalid tick/identity/lifecycle
and owner-thread rejection; affected runtime tests and Javadoc. Then integrate a local
candidate in a real consumer before claiming the paused-resize application defect fixed.
Published dependencies and release approval are separate from this additive source change.
