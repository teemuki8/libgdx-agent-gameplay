package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
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
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.Locale;

/** Explicit codecs for the complete V1 standard component vocabulary. */
public final class StandardComponentCodecs {
    private static final ComponentCodecRegistry REGISTRY = createRegistry();

    private StandardComponentCodecs() {
    }

    /** Returns the immutable standard codec registry. */
    public static ComponentCodecRegistry registry() {
        return REGISTRY;
    }

    private static ComponentCodecRegistry createRegistry() {
        return ComponentCodecRegistry.builder()
                .register(codec(Transform2D.TYPE,
                        Set.of("position", "rotationRadians", "size", "pivot"), fields ->
                                new Transform2D(
                                        fields.optionalVec2("position", Vec2.ZERO),
                                        fields.optionalDouble("rotationRadians", 0.0),
                                        fields.requireVec2("size"),
                                        fields.optionalVec2("pivot", new Vec2(0.5, 0.5)))))
                .register(codec(Movement.TYPE, Set.of("velocity", "maxSpeed"), fields ->
                        new Movement(fields.optionalVec2("velocity", Vec2.ZERO),
                                fields.requireDouble("maxSpeed"))))
                .register(codec(Health.TYPE, Set.of("current", "max"), fields -> {
                    long maximum = fields.requireLong("max");
                    return new Health(fields.optionalLong("current", maximum), maximum);
                }))
                .register(codec(Faction.TYPE, Set.of("value"), fields ->
                        new Faction(fields.requireString("value"))))
                .register(codec(Lifetime.TYPE, Set.of("remainingTicks"), fields ->
                        new Lifetime(fields.requireLong("remainingTicks"))))
                .register(codec(Collider.TYPE,
                        Set.of("shape", "size", "offset", "sensor",
                                "categoryBits", "maskBits"), fields ->
                                new Collider(
                                        Collider.Shape.valueOf(
                                                fields.requireString("shape")
                                                        .toUpperCase(Locale.ROOT)),
                                        fields.requireVec2("size"),
                                        fields.optionalVec2("offset", Vec2.ZERO),
                                        fields.optionalBoolean("sensor", false),
                                        Math.toIntExact(fields.optionalLong("categoryBits", 1)),
                                        Math.toIntExact(fields.optionalLong("maskBits", 0xffff)))))
                .register(codec(Sprite.TYPE,
                        Set.of("asset", "region", "visualSize", "origin"), fields -> {
                            String asset = fields.requireString("asset");
                            return new Sprite(
                                    asset,
                                    fields.optionalString("region", asset),
                                    fields.optionalVec2("visualSize", new Vec2(1, 1)),
                                    fields.optionalVec2("origin", new Vec2(0.5, 0.5)));
                        }))
                .register(codec(Animation.TYPE,
                        Set.of("clips", "currentClip", "elapsedTicks", "frameIndex"),
                        StandardComponentCodecs::animation))
                .register(codec(Render.TYPE,
                        Set.of("layer", "order", "tint", "visible"), fields ->
                                new Render(
                                        fields.optionalString("layer", "world"),
                                        Math.toIntExact(fields.optionalLong("order", 0)),
                                        fields.optionalRgba("tint", Rgba.WHITE),
                                        fields.optionalBoolean("visible", true))))
                .build();
    }

    private static Animation animation(ComponentFields fields) {
        Map<String, AnimationClip> clips = new LinkedHashMap<>();
        for (PrefabValue.ObjectValue object : fields.requireObjects("clips")) {
            ComponentFields clip = ComponentFields.nested(object);
            clip.requireOnly(Set.of("name", "frames", "frameDurationTicks", "loop"));
            String name = clip.requireString("name");
            AnimationClip prior = clips.put(name, new AnimationClip(
                    clip.requireStrings("frames"),
                    clip.requireLong("frameDurationTicks"),
                    clip.optionalBoolean("loop", true)));
            if (prior != null) {
                throw io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException
                        .validation(
                                io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic
                                        .GameplayDiagnosticCode.INVALID_PREFAB_VALUE,
                                "decode-animation",
                                "unique clip names",
                                name,
                                "Declare each animation clip exactly once.");
            }
        }
        String current = fields.requireString("currentClip");
        return new Animation(clips, current,
                fields.optionalLong("elapsedTicks", 0),
                Math.toIntExact(fields.optionalLong("frameIndex", 0)));
    }

    private static <T extends Component> ComponentCodec<T> codec(
            ComponentType<T> type,
            Set<String> fields,
            Function<ComponentFields, T> decoder) {
        return new SimpleCodec<>(type, fields, decoder);
    }

    private record SimpleCodec<T extends Component>(
            ComponentType<T> type,
            Set<String> acceptedFields,
            Function<ComponentFields, T> decoder) implements ComponentCodec<T> {
        private SimpleCodec {
            acceptedFields = Collections.unmodifiableSet(new TreeSet<>(acceptedFields));
        }

        @Override
        public T decode(ComponentFields fields) {
            fields.requireOnly(acceptedFields);
            return decoder.apply(fields);
        }
    }
}
