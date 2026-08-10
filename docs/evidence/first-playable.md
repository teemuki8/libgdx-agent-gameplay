# First playable evidence

## Goal and loop

- Player goal: move inside the bounded arena and destroy the pursuing enemy before contact damage
  exhausts player health.
- Repeatable 30-second loop: enter the arena, reposition with WASD, fire three Space shots at the
  pursuing enemy, observe hit/death feedback and a 300-point reward, then reset to the same seed.
- Immediate feedback: Box2D movement, projectile travel, hit frames, health/score HUD values, and
  collision-attributed events.
- Reward, danger, failure, or recovery: the enemy pursues and damages on contact; three projectile
  contacts trigger a four-frame death sequence and score; reset restores the authored start.

## Reference and design evidence

This is an original fixture rather than a recreation of an existing game. Entries below are design
intent, not claims inferred from third-party footage or assets.

| Aspect | Observed behavior or design intent | Source/evidence | Confidence | Implementation consequence |
|---|---|---|---|---|
| Movement | Immediate eight-direction velocity with stop on genuine key release | Approved V1 design and production-input test | High | `MoveCommand` is bounded to unit direction; Box2D owns resulting translation |
| Rotation and pivot | Centered top-down actors; projectile heading follows the player-to-enemy vector | Approved V1 design | High | centered `0.5,0.5` pivots; projectile rotation is authored at spawn |
| Collision proxy | Simple centered proxies remain slightly inside actor silhouettes | Approved V1 design and prefab assertions | High | actors are 32x32 with 28x28 boxes; projectile is 12x12 with an 8x8 box |
| Scale and camera | Fixed 960x540 orthographic view with 32 render units per metre | Approved V1 design | High | one explicit conversion is shared by bodies, inspection, and alignment evidence |
| HUD | Persistent 80-pixel top strip outside gameplay | Approved V1 design | High | safe region ends at y=436 and top wall ends at y=460 |
| Feedback and consequence | hit frame, four death frames, attributed damage/death, 300 score | Approved V1 design and arena combat test | High | feedback advances on fixed ticks; no rendering callback mutates gameplay |

## Control and simulation ownership

| Degree of freedom or action | Authored or simulated | Production input/command | Update order and bounds | Authoritative state |
|---|---|---|---|---|
| Player velocity intent | Authored | WASD to `MoveCommand` | normalized direction, max 96 units/s in `PRE_PHYSICS` | command stream then native body velocity |
| Player translation/collision | Simulated | resulting Box2D velocity/contact | one 60 Hz step, 6 velocity and 2 position iterations | Box2D body copied out in `POST_PHYSICS` |
| Enemy pursuit intent | Authored simulation system | current player/enemy positions | max 48 units/s before physics | enemy native body velocity |
| Projectile origin/heading | Authored | Space to `AimCommand` and `FireCommand` | one shot per 12 ticks, max 320 units/s | immutable spawn transform and movement |
| Projectile translation/contact | Simulated | Box2D body/contact | 180-tick lifetime, bounded callback queue | Box2D body and stable fixture endpoints |
| Damage/death/score | Authored fixed-tick rules | collision events | one damage per projectile, 3 health, 24-tick death presentation | `Health`, events, and `ArenaGameState` |

- Fixed-step rate: 60 Hz (`16,666,667` ns).
- Network-relevant command boundary: tick-targeted keyboard `MoveCommand`, `AimCommand`, and
  `FireCommand` envelopes from source `keyboard` with monotonic source-local sequence.
- Rendering interpolation or presentation-only behavior: rendering reads completed state; no
  interpolated or sprite value flows back into gameplay.

## Visual and collision alignment

- Visual dimensions, origin, and pivot: player/enemy 32x32, projectile 12x12, all centered.
- Collider type, dimensions, center, and rotation policy: centered Box2D boxes of 28x28 and 8x8;
  projectile body rotation is the authored firing heading.
- Intentional differences between visual and physical representation: two-pixel actor inset and
  two-pixel projectile inset on every side keep collision feedback inside the readable silhouette.
- Debug-render or overlay evidence: the native inspection regression identifies
  `box2d.body.player` and `box2d.fixture.player.collider`, measures the four Box2D polygon
  vertices through the shared 32-units-per-metre conversion, and proves the 28x28 proxy remains
  inside the 32x32 visual. The runtime capture independently reports matching visual bounds and
  camera visibility; no debug overlay was added to the production UI.

## Original art provenance and normalization

All four source images were generated with the built-in image generation tool on 2026-08-10 for
this repository; no third-party asset was used. The transparent sources used flat chroma-key
backgrounds followed by the image skill's local soft-matte/despill removal. Original 1254x1254
outputs and the 256x256 packed atlas were opened and inspected at original resolution.

- Arena floor prompt: orthographic seamless sci-fi training floor, crisp flat colors, deep navy
  and cyan, even lighting, no text/logos/objects/perspective.
- Player prompt: exactly two centered top-down teal-and-white drone frames (idle and hit) on flat
  `#00ff00`, identical registration, no shadow/text/perspective.
- Enemy prompt: exactly six centered top-down crimson-and-charcoal drone frames in a 3x2 grid
  (idle, hit, death 0-3) on flat `#00ff00`, progressive rigid breakup, no shadow/text/perspective.
- Projectile prompt: one centered right-facing cyan/white pixel-readable bolt on flat `#ff00ff`,
  no glow outside the silhouette, shadow, text, or perspective.
- Normalization: crop the declared source cells, trim alpha, fit actors into 28x28 within 32x32
  cells, fit the projectile into 10x10 within a 12x12 cell, construct a mirrored seamless 128x128
  floor tile, and composite all regions into one transparent 256x256 PNG with nearest filtering.
- Packed atlas SHA-256: `139cb684b87101dbe23b78b024abd1733979569a3b1b1541306872c6a2af25d9`.

## Viewport and HUD

- Intended evidence resolution: 960x540.
- Gameplay-safe world region: `(24,24)` through `(936,436)`.
- Persistent HUD regions: top strip y=460 through y=540.
- Reviewed modal or overlap exceptions: title/game-over overlays may cover the arena only while it
  is not actionable; no persistent HUD actor may cover the safe region.

## Production-control evidence

| State | Player-equivalent steps | Screenshot path or opaque receipt | Original resolution | Inspected findings |
|---|---|---|---|---|
| First actionable frame | Query accessible **Start game**, real Enter `Press`, wait for `PLAYING` | [`screenshots/01-actionable.png`](screenshots/01-actionable.png), retained only after receipt SHA/length/type verification | 960x540 | Player and enemy silhouettes are distinct at authored start positions; HUD is wholly above y=460 and the safe region remains clear |
| Primary movement/action response | Real W, D, and Space `Press` actions through the application `InputMultiplexer` | [`screenshots/02-movement-fire.png`](screenshots/02-movement-fire.png) | 960x540 | Player translation is visible and a cyan projectile lies on the direct player-to-enemy firing line; centered sprite/collider policy remains readable |
| Reward state | Two further real Space `Press` actions; wait for enemy health 0 and score 300 | [`screenshots/03-enemy-death.png`](screenshots/03-enemy-death.png) | 960x540 | HUD reports enemy 0 and score 300; the remaining broken red silhouette is the bounded death presentation rather than a live collision target |
| Recovery state | Real Enter `Press` on visible **Reset arena** | [`screenshots/04-reset.png`](screenshots/04-reset.png) | 960x540 | Health, enemy health, score, player position, and enemy position return to the same authored seed |

## Harness and runtime evidence

- Semantic query/action/wait/assert result: the fat-JAR black-box test initialized the production
  stdio MCP, asserted the exact harness 1.2.1 23-tool catalog, resolved exactly one accessible
  **Start game** actor, drove Enter/W/D/Space through `ui_action`, and waited on exact screen,
  health, enemy-health, and score text. The process exited 0 when stdin closed and its bounded
  artifact store contained no files after session teardown.
- `ui_validate_layout` result and located findings: [`layout-validation.json`](layout-validation.json)
  records `PASS` over 24 nodes for all seven checks supported by the first-playable guide:
  outside viewport, clipped text, interactive overlap, zero size, duplicate test ID, missing
  accessible name, and obscured. There are no errors and no `CHECK_UNAVAILABLE` findings. Five
  `OBSCURED` warnings are the documented whole-stage structural parent/internal-widget overlaps;
  their node IDs and bounds remain in the raw result.
- Independent runtime comparison and correlated frame: `screen`, player `health-current`, and
  `score` each reported `EQUAL` with identical displayed/runtime frame IDs before combat, score
  reported `EQUAL` at 300 after combat, and all original values reported `EQUAL` after reset.
  The application captures one presentation runtime frame for every rendered UI frame without
  advancing physics, then records the explicit correlation. A rendered-app regression overrides
  only the displayed score and proves the comparator changes from `EQUAL` to `MISMATCH`.
- Markup 0.5.0 identifier constraint: runtime XML identifiers accept only letters, digits,
  hyphens, and underscores, so markup aliases are `gameplay-arena`, `gameplay-player`,
  `gameplay-enemy`, and `health-current`. Standard gameplay runtime entities retain their
  documented dotted runtime-only IDs such as `gameplay.entity.player`.
- Input capability gaps: harness 1.2.1 Press is sufficient for the one-tick movement pulse and
  each Space shot; no alternate control is introduced.

## Walkthrough findings

- Learnability and first objective: the title names the objective and Enter action; the actionable
  HUD repeats the WASD and Space controls without adding automation-only controls.
- Control feel and pivot: W then D produces a short, immediate production-path movement pulse;
  the centered drone pivot is stable. Harness 1.2.1 cannot evaluate sustained held-key feel, so
  long-form tuning remains a subjective manual concern rather than an automated pass threshold.
- Feedback and readability: teal player, crimson enemy, and cyan projectile remain distinguishable
  over the low-contrast navy floor at 960x540. The original layer names initially placed the floor
  over characters; original-size inspection caught it and the final lexical order is explicitly
  background, characters, projectiles.
- Failure/recovery clarity: reset is persistent, accessible, and visibly restores the same health,
  score, and spatial state. Contact damage/game-over logic has deterministic tests; this evidence
  loop captures reward and reset because three direct shots complete before enemy contact.
- Reward and reason to repeat: enemy health counts down independently and the 300-point reward is
  visible immediately; deterministic reset supports replaying movement and firing choices.

## Open risks and next decision

- Unresolved feel or art risks: 32-pixel actors are intentionally compact at 960x540, and sustained
  movement/pursuit balance still merits human playtesting; neither is converted into an arbitrary
  automated threshold.
- Deliberate exceptions: whole-stage `OBSCURED` warnings are retained as located diagnostics for
  Scene2D structural parents and internal button actors; there are no actionable overlap errors.
- Smallest next change supported by this evidence: freeze the canonical arena as the integration
  fixture and proceed to public API documentation, isolated Maven-consumer verification, and CI.
