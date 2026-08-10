package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, validated components reusable for deterministic entity instantiation. */
public record PrefabDefinition(
        PrefabId id,
        Map<ComponentType<?>, Component> components) {
    /** Copies and sorts the component map. */
    public PrefabDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(components, "components");
        components = Collections.unmodifiableMap(new TreeMap<>(components));
    }

    /** Creates a detached entity draft with the declared components. */
    public EntityDraft instantiate(EntityId entityId) {
        return instantiateInternal(entityId, null);
    }

    /** Creates a detached entity draft with only its initial transform replaced. */
    public EntityDraft instantiate(EntityId entityId, Transform2D transformOverride) {
        return instantiateInternal(entityId, Objects.requireNonNull(
                transformOverride, "transformOverride"));
    }

    private EntityDraft instantiateInternal(EntityId entityId, Transform2D transformOverride) {
        EntityDraft.Builder builder = EntityDraft.builder(entityId);
        components.forEach((type, component) -> add(builder, type,
                type.equals(Transform2D.TYPE) && transformOverride != null
                        ? transformOverride : component));
        if (transformOverride != null && !components.containsKey(Transform2D.TYPE)) {
            builder.with(Transform2D.TYPE, transformOverride);
        }
        return builder.build();
    }

    private static <T extends Component> void add(
            EntityDraft.Builder builder,
            ComponentType<T> type,
            Component component) {
        builder.with(type, type.valueClass().cast(component));
    }
}
