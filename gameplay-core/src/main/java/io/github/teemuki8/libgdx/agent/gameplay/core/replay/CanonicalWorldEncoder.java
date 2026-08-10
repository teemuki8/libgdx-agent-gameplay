package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Faction;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Lifetime;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionEnded;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityDespawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityKilled;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntitySpawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ItemCollected;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ObjectiveCompleted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ProjectileCreated;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Canonical bounded encoder for completed world state and tick-local events.
 *
 * <p>Strings are length-prefixed UTF-8, integral values are big-endian, and decimal values use
 * canonical IEEE-754 bits with negative zero normalized to positive zero.</p>
 */
public final class CanonicalWorldEncoder {
    private final int maxBytes;

    /** Creates an encoder with an application-lowered cap. */
    public CanonicalWorldEncoder(int maxBytes) {
        if (maxBytes < 1 || maxBytes > GameplayLimits.SNAPSHOT_BYTE_MAXIMUM) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "configure-canonical-encoder",
                    "maxBytes in [1," + GameplayLimits.SNAPSHOT_BYTE_MAXIMUM + "]",
                    Integer.toString(maxBytes),
                    "Choose a positive cap no greater than the V1 maximum.");
        }
        this.maxBytes = maxBytes;
    }

    /** Returns an encoder using the fixed 4 MiB V1 cap. */
    public static CanonicalWorldEncoder defaults() {
        return new CanonicalWorldEncoder(GameplayLimits.SNAPSHOT_BYTE_MAXIMUM);
    }

    /** Encodes one completed world snapshot in canonical entity/component order. */
    public byte[] encode(WorldSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Writer writer = new Writer(maxBytes);
        writer.text("world-snapshot/1");
        writer.longValue(snapshot.tick());
        List<EntitySnapshot> entities = snapshot.entities().stream().sorted().toList();
        writer.integer(entities.size());
        for (EntitySnapshot entity : entities) {
            writer.text(entity.id().value());
            writer.text(entity.state().name());
            writer.integer(entity.components().size());
            entity.components().forEach((type, component) -> {
                writer.text(type.id());
                encodeComponent(writer, component);
            });
        }
        return writer.toByteArray();
    }

    /** Returns the SHA-256 digest of one canonical world snapshot. */
    public WorldDigest digest(WorldSnapshot snapshot) {
        return new WorldDigest(snapshot.tick(), sha256(encode(snapshot)));
    }

    /** Encodes one tick's events in tick-local sequence order. */
    public byte[] encodeEvents(long tick, List<EventEnvelope> events) {
        if (tick < 0) {
            throw unsupported("non-negative event tick", Long.toString(tick));
        }
        Objects.requireNonNull(events, "events");
        Writer writer = new Writer(maxBytes);
        writer.text("gameplay-events/1");
        writer.longValue(tick);
        List<EventEnvelope> ordered = events.stream()
                .map(event -> Objects.requireNonNull(event, "event"))
                .sorted(Comparator.comparingLong(EventEnvelope::sequence))
                .toList();
        writer.integer(ordered.size());
        long priorSequence = -1;
        for (EventEnvelope envelope : ordered) {
            if (envelope.tick() != tick || envelope.sequence() <= priorSequence) {
                throw unsupported("matching tick and unique ascending event sequence",
                        envelope.tick() + ":" + envelope.sequence());
            }
            priorSequence = envelope.sequence();
            writer.longValue(envelope.sequence());
            encodeEvent(writer, envelope.event());
            writer.integer(envelope.attributes().values().size());
            envelope.attributes().values().forEach((name, value) -> {
                writer.text(name);
                encodeAttribute(writer, value);
            });
        }
        return writer.toByteArray();
    }

    /** Returns the SHA-256 digest of one canonical event record. */
    public WorldDigest digestEvents(long tick, List<EventEnvelope> events) {
        return new WorldDigest(tick, sha256(encodeEvents(tick, events)));
    }

    private static void encodeComponent(Writer writer, Component component) {
        if (component instanceof Transform2D transform) {
            vector(writer, transform.position());
            writer.decimal(transform.rotationRadians());
            vector(writer, transform.size());
            vector(writer, transform.pivot());
        } else if (component instanceof Movement movement) {
            vector(writer, movement.velocity());
            writer.decimal(movement.maxSpeed());
        } else if (component instanceof Health health) {
            writer.longValue(health.current());
            writer.longValue(health.max());
        } else if (component instanceof Faction faction) {
            writer.text(faction.value());
        } else if (component instanceof Lifetime lifetime) {
            writer.longValue(lifetime.remainingTicks());
        } else if (component instanceof Collider collider) {
            writer.text(collider.shape().name());
            vector(writer, collider.size());
            vector(writer, collider.offset());
            writer.bool(collider.sensor());
            writer.integer(collider.categoryBits());
            writer.integer(collider.maskBits());
        } else if (component instanceof Sprite sprite) {
            writer.text(sprite.asset());
            writer.text(sprite.region());
            vector(writer, sprite.visualSize());
            vector(writer, sprite.origin());
        } else if (component instanceof Animation animation) {
            encodeAnimation(writer, animation);
        } else if (component instanceof Render render) {
            writer.text(render.layer());
            writer.integer(render.order());
            color(writer, render.tint());
            writer.bool(render.visible());
        } else {
            throw unsupported("standard canonical component",
                    component.getClass().getName());
        }
    }

    private static void encodeAnimation(Writer writer, Animation animation) {
        Map<String, AnimationClip> clips = new TreeMap<>(animation.clips());
        writer.integer(clips.size());
        clips.forEach((name, clip) -> {
            writer.text(name);
            writer.integer(clip.frames().size());
            clip.frames().forEach(writer::text);
            writer.longValue(clip.frameDurationTicks());
            writer.bool(clip.loop());
        });
        writer.text(animation.currentClip());
        writer.longValue(animation.elapsedTicks());
        writer.integer(animation.frameIndex());
    }

    private static void encodeEvent(Writer writer, GameplayEvent event) {
        if (event instanceof EntitySpawned spawned) {
            writer.text("entity-spawned");
            writer.text(spawned.subject().value());
        } else if (event instanceof EntityDespawned despawned) {
            writer.text("entity-despawned");
            writer.text(despawned.subject().value());
        } else if (event instanceof DamageApplied damage) {
            writer.text("damage-applied");
            writer.text(damage.subject().value());
            writer.text(damage.source().value());
            writer.longValue(damage.amount());
        } else if (event instanceof EntityKilled killed) {
            writer.text("entity-killed");
            writer.text(killed.subject().value());
            writer.text(killed.source().value());
        } else if (event instanceof ItemCollected collected) {
            writer.text("item-collected");
            writer.text(collected.subject().value());
            writer.text(collected.item().value());
        } else if (event instanceof ProjectileCreated projectile) {
            writer.text("projectile-created");
            writer.text(projectile.subject().value());
            writer.text(projectile.source().value());
        } else if (event instanceof ObjectiveCompleted objective) {
            writer.text("objective-completed");
            writer.text(objective.subject().value());
            writer.text(objective.objectiveId());
        } else if (event instanceof CollisionStarted collision) {
            writer.text("collision-started");
            collision(writer, collision.first().value(), collision.second().value(),
                    collision.firstFixtureId(), collision.secondFixtureId());
        } else if (event instanceof CollisionEnded collision) {
            writer.text("collision-ended");
            collision(writer, collision.first().value(), collision.second().value(),
                    collision.firstFixtureId(), collision.secondFixtureId());
        } else {
            throw unsupported("standard canonical event", event.getClass().getName());
        }
    }

    private static void collision(
            Writer writer, String first, String second, String firstFixture, String secondFixture) {
        writer.text(first);
        writer.text(second);
        writer.text(firstFixture);
        writer.text(secondFixture);
    }

    private static void encodeAttribute(Writer writer, EventAttributeValue value) {
        if (value instanceof EventAttributeValue.StringValue string) {
            writer.text("string");
            writer.text(string.value());
        } else if (value instanceof EventAttributeValue.IntegerValue integer) {
            writer.text("integer");
            writer.longValue(integer.value());
        } else if (value instanceof EventAttributeValue.DecimalValue decimal) {
            writer.text("decimal");
            writer.decimal(decimal.value());
        } else if (value instanceof EventAttributeValue.BooleanValue bool) {
            writer.text("boolean");
            writer.bool(bool.value());
        } else if (value instanceof EventAttributeValue.EntityValue entity) {
            writer.text("entity");
            writer.text(entity.value().value());
        } else {
            throw unsupported("canonical typed event attribute", value.getClass().getName());
        }
    }

    private static void vector(Writer writer, Vec2 value) {
        writer.decimal(value.x());
        writer.decimal(value.y());
    }

    private static void color(Writer writer, Rgba value) {
        writer.decimal(value.red());
        writer.decimal(value.green());
        writer.decimal(value.blue());
        writer.decimal(value.alpha());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK does not provide SHA-256", failure);
        }
    }

    private static GameplayException unsupported(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.UNSUPPORTED_CANONICAL_VALUE,
                "encode-canonical-record",
                expected,
                observed,
                "Register and encode only the closed V1 canonical vocabulary.");
    }

    private static final class Writer {
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Writer(int limit) {
            this.limit = limit;
        }

        private void bool(boolean value) {
            octet(value ? 1 : 0);
        }

        private void decimal(double value) {
            if (!Double.isFinite(value)) {
                throw unsupported("finite canonical decimal", Double.toString(value));
            }
            double normalized = value == 0.0 ? 0.0 : value;
            longValue(Double.doubleToLongBits(normalized));
        }

        private void integer(int value) {
            ensure(Integer.BYTES);
            for (int shift = 24; shift >= 0; shift -= 8) {
                output.write(value >>> shift & 0xff);
            }
        }

        private void longValue(long value) {
            ensure(Long.BYTES);
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) (value >>> shift & 0xff));
            }
        }

        private void octet(int value) {
            ensure(1);
            output.write(value);
        }

        private void text(String value) {
            byte[] utf8 = Objects.requireNonNull(value, "canonical text")
                    .getBytes(StandardCharsets.UTF_8);
            integer(utf8.length);
            ensure(utf8.length);
            output.writeBytes(utf8);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }

        private void ensure(int additionalBytes) {
            if (additionalBytes > limit - output.size()) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.SNAPSHOT_LIMIT_EXCEEDED,
                        "encode-canonical-record",
                        "at most " + limit + " bytes",
                        Integer.toString(output.size() + additionalBytes),
                        "Lower entity/event volume or raise the cap within the V1 maximum.");
            }
        }
    }
}
