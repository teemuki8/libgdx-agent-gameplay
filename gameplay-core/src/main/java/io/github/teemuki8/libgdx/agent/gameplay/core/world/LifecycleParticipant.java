package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;

/** Adapter callbacks at deterministic entity/native-resource lifecycle barriers. */
public interface LifecycleParticipant {
    /** Dependency level; larger values dispose before smaller values. */
    default int dependencyLevel() {
        return 0;
    }

    /** Called in registration order at entity activation. */
    default void onActivate(EntityView entity) {
    }

    /** Called after gameplay when an entity becomes logically invisible. */
    default void onLogicalDespawn(EntityView entity) {
    }

    /** Called after runtime capture to release participant-owned native mappings. */
    default void onDispose(EntityId entityId) {
    }

    /** Called after all current entities are disposed at reset. */
    default void onReset() {
    }

    /** Called during world close after entity disposal. */
    default void onClose() {
    }
}
