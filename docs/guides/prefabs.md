# Strict prefabs

`PrefabParser` reads the bounded `gameplay-prefabs/1` JSON schema with Jackson streaming APIs. A
catalog maps one semantic prefab ID to a detached immutable component set. Instantiation copies the
definition under a caller-supplied entity ID; it never spawns directly or touches libGDX.

Use `StandardComponentCodecs.registry()` for the V1 component vocabulary and explicit
`PrefabLimits`. The parser rejects duplicate JSON keys, unknown fields, duplicate prefab IDs,
duplicate component types, unsupported schema versions, trailing content, excessive depth/count,
and oversized input. It does not preserve unknown data for forward compatibility.

```java
PrefabCatalog catalog = new PrefabParser(
        StandardComponentCodecs.registry(), PrefabLimits.defaults()).parse(jsonBytes);
EntityDraft player = catalog.require(PrefabId.of("player"))
        .instantiate(EntityId.of("player-one"));
world.spawn(player);
```

Treat prefab bytes as configuration, not authority. Validate them before creating GL or native
resources. Keep semantic IDs stable because commands, replay digests, runtime entities, Box2D
fixtures, and agent evidence all refer to them.
