package io.github.teemuki8.libgdx.agent.gameplay.core.event;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Explicit bounded custom-event codecs shared by canonical replay and runtime projection. */
public final class EventCodecRegistry {
    private static final Set<Class<?>> STANDARD_CLASSES = Set.of(EntitySpawned.class, EntityDespawned.class,
            DamageApplied.class, EntityKilled.class, ItemCollected.class, ProjectileCreated.class,
            ObjectiveCompleted.class, CollisionStarted.class, CollisionEnded.class, CollisionImpact.class);
    private static final Set<String> STANDARD_IDS = Set.of("entity-spawned", "entity-despawned",
            "damage-applied", "entity-killed", "item-collected", "projectile-created",
            "objective-completed", "collision-started", "collision-ended", "collision-impact");
    private final Map<Class<?>, Function<GameplayEvent, Projection>> codecs;

    private EventCodecRegistry(Map<Class<?>, Function<GameplayEvent, Projection>> codecs) {
        this.codecs = Map.copyOf(codecs);
    }

    /** Starts a registry with no custom codecs; standard events keep their existing representation. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns an empty custom registry. */
    public static EventCodecRegistry empty() {
        return new EventCodecRegistry(Map.of());
    }

    /** Copies a registered event to closed immutable evidence, without inspecting unknown classes. */
    public Optional<Projection> project(GameplayEvent event) {
        Objects.requireNonNull(event, "event");
        Function<GameplayEvent, Projection> codec = codecs.get(event.getClass());
        return codec == null ? Optional.empty() : Optional.of(codec.apply(event));
    }

    /** Projects once and rejects ambiguous or oversized custom-plus-envelope attribute sets. */
    public Optional<Projection> project(GameplayEvent event, EventAttributes envelopeAttributes) {
        Objects.requireNonNull(envelopeAttributes, "envelopeAttributes");
        Optional<Projection> result = project(event);
        result.ifPresent(projection -> {
            Map<String, EventAttributeValue> merged = new HashMap<>(projection.payload().attributes().values());
            envelopeAttributes.values().forEach((name, value) -> {
                if (merged.putIfAbsent(name, value) != null) {
                    throw invalid("distinct custom payload and envelope attribute names");
                }
            });
            EventAttributes.of(merged);
        });
        return result;
    }

    /** Detached subject/source and bounded scalar fields; codecs must copy every relevant value. */
    public record Payload(Optional<EntityId> subject, Optional<EntityId> source, EventAttributes attributes) {
        /** Validates already immutable values. */
        public Payload {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(attributes, "attributes");
        }
    }

    /** Registered stable custom event identity and detached evidence. */
    public record Projection(String type, Payload payload) {
        /** Validates a bounded type that leaves room for the runtime's gameplay namespace. */
        public Projection {
            IdentifierRules.requireIdentifier(type, "eventType");
            if (type.length() > 128 || STANDARD_IDS.contains(type)) {
                throw invalid("distinct custom event ID with at most 128 characters");
            }
            Objects.requireNonNull(payload, "payload");
        }
    }

    /** Builds an immutable registry of at most 256 explicitly supplied classes and unique IDs. */
    public static final class Builder {
        private final Map<Class<?>, Function<GameplayEvent, Projection>> codecs = new HashMap<>();
        private final Set<String> ids = new HashSet<>();

        private Builder() {}

        /** Registers a deterministic copying codec; inheritance dispatch and class loading are absent. */
        public <T extends GameplayEvent> Builder register(
                String type, Class<T> eventClass, Function<T, Payload> codec) {
            Objects.requireNonNull(eventClass, "eventClass");
            Objects.requireNonNull(codec, "codec");
            new Projection(type, new Payload(Optional.empty(), Optional.empty(), EventAttributes.empty()));
            if (codecs.size() >= 256 || ids.contains(type) || codecs.containsKey(eventClass)
                    || STANDARD_CLASSES.contains(eventClass)) {
                throw invalid("at most 256 unique custom IDs and classes, without standard overrides");
            }
            codecs.put(eventClass, event -> new Projection(type, codec.apply(eventClass.cast(event))));
            ids.add(type);
            return this;
        }

        /** Copies the registrations; subsequent builder changes cannot affect this registry. */
        public EventCodecRegistry build() {
            return new EventCodecRegistry(codecs);
        }
    }

    private static GameplayException invalid(String expected) {
        return GameplayException.validation(GameplayDiagnosticCode.INVALID_EVENT_CODEC,
                "register-event-codec", expected, "invalid event registration",
                "Register a distinct application event class and stable ID before simulation.");
    }
}
