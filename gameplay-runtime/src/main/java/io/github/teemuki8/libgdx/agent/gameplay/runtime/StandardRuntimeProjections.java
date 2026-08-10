package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Faction;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Lifetime;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/** Explicit projections for all V1 standard gameplay components. */
public final class StandardRuntimeProjections {
    private static final RuntimeProjectionRegistry REGISTRY = RuntimeProjectionRegistry.builder()
            .register(projection(Transform2D.TYPE, (entity, value) -> Map.of(
                    "transform.position", vector(value.position()),
                    "transform.rotation", RuntimeValues.decimal(value.rotationRadians()),
                    "transform.size", vector(value.size()),
                    "transform.pivot", vector(value.pivot()))))
            .register(projection(Movement.TYPE, (entity, value) -> Map.of(
                    "movement.velocity", vector(value.velocity()),
                    "movement.maxSpeed", RuntimeValues.decimal(value.maxSpeed()))))
            .register(projection(Health.TYPE, (entity, value) -> Map.of(
                    "health.current", RuntimeValues.integer(value.current()),
                    "health.max", RuntimeValues.integer(value.max()),
                    "health.alive", RuntimeValues.bool(value.current() > 0))))
            .register(projection(Faction.TYPE, (entity, value) -> Map.of(
                    "faction.value", RuntimeValues.enumValue(value.value()))))
            .register(projection(Lifetime.TYPE, (entity, value) -> Map.of(
                    "lifetime.remainingTicks",
                    RuntimeValues.integer(value.remainingTicks()))))
            .register(projection(Collider.TYPE, (entity, value) -> Map.of(
                    "collider.shape", RuntimeValues.enumValue(value.shape().name()),
                    "collider.size", vector(value.size()),
                    "collider.offset", vector(value.offset()),
                    "collider.sensor", RuntimeValues.bool(value.sensor()),
                    "collider.categoryBits", RuntimeValues.integer(value.categoryBits()),
                    "collider.maskBits", RuntimeValues.integer(value.maskBits()))))
            .register(projection(Sprite.TYPE, (entity, value) -> Map.of(
                    "sprite.asset", RuntimeValues.string(value.asset()),
                    "sprite.region", RuntimeValues.string(value.region()),
                    "sprite.visualSize", vector(value.visualSize()),
                    "sprite.origin", vector(value.origin()))))
            .register(projection(Animation.TYPE, (entity, value) -> Map.of(
                    "animation.currentClip", RuntimeValues.enumValue(value.currentClip()),
                    "animation.elapsedTicks", RuntimeValues.integer(value.elapsedTicks()),
                    "animation.frameIndex", RuntimeValues.integer(value.frameIndex()),
                    "animation.clipCount", RuntimeValues.integer(value.clips().size()))))
            .register(projection(Render.TYPE, (entity, value) -> Map.of(
                    "render.layer", RuntimeValues.enumValue(value.layer()),
                    "render.order", RuntimeValues.integer(value.order()),
                    "render.tint", color(value.tint()),
                    "render.visible", RuntimeValues.bool(value.visible()))))
            .build();

    private StandardRuntimeProjections() {
    }

    /** Returns the immutable standard projection registry. */
    public static RuntimeProjectionRegistry registry() {
        return REGISTRY;
    }

    private static RuntimeValue vector(Vec2 value) {
        return RuntimeValues.vector2(value.x(), value.y());
    }

    private static RuntimeValue color(Rgba value) {
        return RuntimeValues.object(
                RuntimeValues.field("red", RuntimeValues.decimal(value.red())),
                RuntimeValues.field("green", RuntimeValues.decimal(value.green())),
                RuntimeValues.field("blue", RuntimeValues.decimal(value.blue())),
                RuntimeValues.field("alpha", RuntimeValues.decimal(value.alpha())));
    }

    private static <T extends Component> RuntimeProjection<T> projection(
            ComponentType<T> type,
            BiFunction<EntitySnapshot, T, Map<String, RuntimeValue>> function) {
        return new SimpleProjection<>(type, function);
    }

    private record SimpleProjection<T extends Component>(
            ComponentType<T> componentType,
            BiFunction<EntitySnapshot, T, Map<String, RuntimeValue>> function)
            implements RuntimeProjection<T> {
        @Override
        public Map<String, RuntimeValue> project(EntitySnapshot entity, T component) {
            return Collections.unmodifiableMap(new TreeMap<>(
                    function.apply(entity, component)));
        }
    }
}
