package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.List;
import java.util.Optional;

/** Immutable authoritative entity state after one completed tick. */
public record WorldSnapshot(long tick, List<EntitySnapshot> entities) {
    /** Sorts and copies all entity snapshots. */
    public WorldSnapshot {
        entities = entities.stream().sorted().toList();
    }

    /** Resolves a semantic entity ID in this completed snapshot. */
    public Optional<EntitySnapshot> entity(EntityId id) {
        return entities.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
    }
}
