# Three-dimensional authoritative state

The additive GL-free vocabulary uses the existing `GameWorld`, component registry, fixed ticks,
strict `gameplay-prefabs/1` parser and `CommandTranscript`. Existing 2D types and field encodings
are unchanged. No new world, command serializer or runtime store is introduced.

- `Vec3(x,y,z)` accepts finite doubles; physics adapters further constrain world extents.
- `QuaternionValue(x,y,z,w)` represents right-handed active rotation. It requires squared length
  within `1e-6` of one (including native float round trips), rejects zero/nonfinite values, and
  never silently normalizes. Equivalent opposite-sign quaternions remain distinct authored data.
- `Transform3D(position,rotation,scale)` requires positive scale. The two-argument constructor
  supplies unit scale. Scale is visual; collision dimensions are explicit adapter configuration.
- `Velocity3D(linear)` is authoritative world velocity in units per second.
- `Aim3D(yawRadians,pitchRadians)` uses Y-up, negative-Z forward, positive yaw right and positive
  pitch up. Yaw must be in `[-pi,pi]`, pitch in `[-pi/2,pi/2]`. Input adapters wrap/clamp explicitly.
  `direction()` returns `(sin(yaw)*cos(pitch),sin(pitch),-cos(yaw)*cos(pitch))` using StrictMath.

`Move3DCommand(entityId,direction)`, `Aim3DCommand(entityId,aim)` and
`Fire3DCommand(entityId,origin,direction)` are immutable typed intents. Direction vectors are finite
and retain their supplied magnitude, as in the existing 2D commands; a game's controller chooses
normalization, movement-speed and fire-policy rules. They enter through the existing bounded
command envelope/queue/transcript. Systems consume commands on the world owner thread; no input
polling, native resource or rendering object enters these values. Simulation/native determinism
still requires adapter-specific qualification; reproducible core encoding is not that evidence.

```json
{"schemaVersion":"gameplay-prefabs/1","prefabs":[{"id":"player","components":[
  {"type":"transform3d","position":[0,1,0],"rotation":[0,0,0,1],"scale":[1,1,1]},
  {"type":"velocity3d","linear":[0,0,0]},
  {"type":"aim3d","yawRadians":0,"pitchRadians":0}
]}]}
```

All fields are closed. Transform defaults to zero position, identity rotation and unit scale;
velocity requires `linear` and aim requires both angles. Wrong lengths, unknown/duplicate fields,
nonfinite conversions and invalid rotations/scales fail through the existing located diagnostics.
The standard component registry installs immutable copying and explicit canonical field encoding.
Replay includes every pose, scale, velocity and angle coordinate; presentation never rewrites them.

The standard runtime projection registry exposes `transform3d.position`, `transform3d.rotation`,
`transform3d.scale`, `velocity3d.linear`, `aim3d.yawRadians`, `aim3d.pitchRadians`, and
`aim3d.direction`. Vectors are typed objects with decimal `x/y/z` fields; rotations additionally have
`w`. This preserves the published runtime API without adding an unqualified vector wire type.
Values come exclusively from completed authoritative snapshots and retain existing projection
limits/frame correlation. Stage/Actor work remains render-thread confined in the application.
