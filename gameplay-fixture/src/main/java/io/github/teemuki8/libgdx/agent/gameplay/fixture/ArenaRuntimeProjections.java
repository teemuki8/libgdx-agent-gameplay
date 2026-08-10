package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.RuntimeProjection;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.RuntimeProjectionRegistry;
import io.github.teemuki8.libgdx.agent.gameplay.runtime.StandardRuntimeProjections;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import java.util.Map;

/** Explicit standard plus fixture-state runtime projection registry. */
public final class ArenaRuntimeProjections {
    private static final RuntimeProjectionRegistry REGISTRY = create();

    private ArenaRuntimeProjections() {
    }

    /** Returns the immutable projection registry used by the full fixture. */
    public static RuntimeProjectionRegistry registry() {
        return REGISTRY;
    }

    private static RuntimeProjectionRegistry create() {
        RuntimeProjectionRegistry.Builder builder = RuntimeProjectionRegistry.builder();
        StandardRuntimeProjections.registry().projections().forEach(builder::register);
        return builder.register(new RuntimeProjection<ArenaStateComponent>() {
            @Override public ComponentType<ArenaStateComponent> componentType() {
                return ArenaStateComponent.TYPE;
            }

            @Override public Map<String, RuntimeValue> project(
                    EntitySnapshot entity, ArenaStateComponent value) {
                return Map.of(
                        "arena.screen", RuntimeValues.enumValue(value.screen().name()),
                        "arena.score", RuntimeValues.integer(value.score()),
                        "arena.aim", RuntimeValues.vector2(
                                value.aimDirection().x(), value.aimDirection().y()),
                        "arena.enemyKilled", RuntimeValues.bool(value.enemyKilled()),
                        "arena.playerKilled", RuntimeValues.bool(value.playerKilled()));
            }
        }).build();
    }
}
