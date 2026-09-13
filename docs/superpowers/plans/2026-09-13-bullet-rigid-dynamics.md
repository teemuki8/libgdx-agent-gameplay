# Bullet rigid dynamics implementation plan

1. Add immutable, bounded public specifications for primitive rigid bodies and paired
   six-degree constraints. Stable gameplay IDs are the only public identity.
2. Add native tests first for falling bodies, impulse transfer, constrained endpoints,
   body/constraint ceilings, invalid geometry, owner-thread access and disposal.
3. Implement an application-owned `GameplayBulletDynamicsWorld` using one Bullet discrete
   dynamics world. It steps only when called with the application's fixed step, copies all
   observable transforms and velocities, and never exposes native objects.
4. Keep shapes, motion states, rigid bodies and constraints private and dispose constraints
   before their endpoint bodies. Reject removal with live constraints.
5. Run the gameplay-bullet native tests and complete repository gate before the bootstrap
   consumes the candidate API.

The library chooses no humanoid proportions, death rule, controller, camera, rendering,
player count or multiplayer policy. Those belong to the consuming examples.
