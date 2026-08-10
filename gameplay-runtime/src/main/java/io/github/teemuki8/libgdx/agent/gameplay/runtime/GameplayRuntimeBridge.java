package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityDespawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityKilled;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntitySpawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ItemCollected;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ObjectiveCompleted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ProjectileCreated;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualEntry;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntimeException;
import io.github.teemuki8.libgdx.agent.runtime.core.ChangeCause;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityRegistration;
import io.github.teemuki8.libgdx.agent.runtime.core.EntityType;
import io.github.teemuki8.libgdx.agent.runtime.core.EventId;
import io.github.teemuki8.libgdx.agent.runtime.core.EventSpec;
import io.github.teemuki8.libgdx.agent.runtime.core.InspectableEntity;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Capture-thread bridge from immutable gameplay evidence to one caller-owned agent runtime.
 *
 * <p>The bridge owns only its dynamic source registration. It never closes the runtime and never
 * records a UI correlation before the application has rendered the corresponding frame.</p>
 */
public final class GameplayRuntimeBridge implements AutoCloseable {
    private static final String SOURCE_NAME = "gameplay";
    private static final EntityType FRAME_TYPE = EntityType.of("gameplay-frame");
    private static final EntityType ENTITY_TYPE = EntityType.of("gameplay-entity");
    private static final EntityType VISUAL_TYPE = EntityType.of("gameplay-visual");

    private final AgentRuntime runtime;
    private final RuntimeProjectionRegistry projections;
    private final GameplayLimits limits;
    private final EntityRegistration sourceRegistration;
    private final List<GameSystem> systems;
    private volatile GameplayRuntimeFrame capturedFrame;
    private WorldVisualSnapshot preparedVisuals;
    private long openTick = -1;
    private boolean closed;
    private String lastFrameToken;

    /** Installs one bounded dynamic source before the caller starts the runtime. */
    public GameplayRuntimeBridge(
            AgentRuntime runtime,
            RuntimeProjectionRegistry projections,
            GameplayLimits limits) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.limits = Objects.requireNonNull(limits, "limits");
        try {
            sourceRegistration = runtime.entities().registerSource(
                    SOURCE_NAME, this::runtimeEntities);
        } catch (AgentRuntimeException failure) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.RUNTIME_DUPLICATE_INSTALLATION,
                    "install-gameplay-runtime",
                    "one gameplay source per AgentRuntime",
                    failure.code().name(),
                    "Close the existing gameplay bridge before installing another.");
        }
        systems = List.of(new RuntimeOpenFrameSystem(this), new RuntimeCaptureSystem(this));
    }

    /** Returns the fixed INPUT-open and RUNTIME_CAPTURE-complete systems. */
    public List<GameSystem> systems() {
        requireOpen();
        return systems;
    }

    /** Publishes immutable visual evidence produced during this tick's RENDER_PREP phase. */
    public void prepareVisuals(WorldVisualSnapshot visuals) {
        requireOpen();
        WorldVisualSnapshot checked = Objects.requireNonNull(visuals, "visuals");
        if (openTick < 0 || checked.tick() != openTick) {
            throw incomplete("visuals for open tick " + openTick,
                    Long.toString(checked.tick()),
                    "Publish visual evidence after frame open and before runtime capture.");
        }
        preparedVisuals = checked;
    }

    /** Captures and completes the currently open runtime frame exactly once. */
    public void capture(GameplayRuntimeFrame frame) {
        requireOpen();
        GameplayRuntimeFrame checked = Objects.requireNonNull(frame, "frame");
        if (openTick < 0 || checked.world().tick() != openTick) {
            throw incomplete("runtime frame for open tick " + openTick,
                    Long.toString(checked.world().tick()),
                    "Run RuntimeOpenFrameSystem and RuntimeCaptureSystem once per tick.");
        }
        int exposedEntities = Math.addExact(1,
                Math.multiplyExact(checked.world().entities().size(), 2));
        int runtimeEntityLimit = runtime.configuration().limits().entitiesPerSnapshot();
        if (exposedEntities > runtimeEntityLimit
                || checked.world().entities().size() > limits.maxEntities()
                || checked.events().size() > limits.maxEventsPerTick()
                || checked.visuals().entries().size() > limits.maxVisualEntries()) {
            throw incomplete("evidence within gameplay and runtime entity/event/visual limits",
                    exposedEntities + ":" + checked.events().size() + ":"
                            + checked.visuals().entries().size(),
                    "Lower capture volume rather than accepting truncated authority.");
        }
        capturedFrame = checked;
        Throwable failure = null;
        try {
            emitEvents(checked.events());
            runtime.endFrame();
            lastFrameToken = checked.frameToken();
        } catch (Throwable captureFailure) {
            failure = captureFailure;
            try {
                runtime.endFrame();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != captureFailure) {
                    captureFailure.addSuppressed(cleanupFailure);
                }
            }
        } finally {
            openTick = -1;
            preparedVisuals = null;
        }
        if (failure != null) {
            rethrow(failure);
        }
    }

    /** Returns the last successfully completed gameplay frame token. */
    public Optional<String> lastFrameToken() {
        return Optional.ofNullable(lastFrameToken);
    }

    void openFrame(long tick, long deltaNanos) {
        requireOpen();
        if (openTick >= 0) {
            long incompleteTick = openTick;
            runtime.endFrame();
            openTick = -1;
            preparedVisuals = null;
            throw incomplete("previous runtime frame completed", Long.toString(incompleteTick),
                    "Ensure RuntimeCaptureSystem executes once after RENDER_PREP.");
        }
        runtime.beginFrame(deltaNanos);
        openTick = tick;
    }

    WorldVisualSnapshot requirePreparedVisuals(long tick) {
        requireOpen();
        if (preparedVisuals == null || preparedVisuals.tick() != tick) {
            throw incomplete("visual evidence for tick " + tick,
                    preparedVisuals == null ? "missing" : Long.toString(preparedVisuals.tick()),
                    "Run visual snapshot preparation in RENDER_PREP every tick.");
        }
        return preparedVisuals;
    }

    private Stream<InspectableEntity> runtimeEntities() {
        GameplayRuntimeFrame frame = capturedFrame;
        if (frame == null) {
            return Stream.empty();
        }
        ArrayList<InspectableEntity> entities = new ArrayList<>();
        entities.add(frameEntity(frame));
        Map<io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId, WorldVisualEntry>
                visuals = new HashMap<>();
        frame.visuals().entries().forEach(entry -> visuals.put(entry.entityId(), entry));
        for (EntitySnapshot entity : frame.world().entities()) {
            entities.add(gameplayEntity(frame, entity));
            entities.add(visualEntity(frame, entity, visuals.get(entity.id())));
        }
        return entities.stream();
    }

    private InspectableEntity frameEntity(GameplayRuntimeFrame frame) {
        Map<String, RuntimeValue> values = Map.of(
                "tick", RuntimeValues.integer(frame.world().tick()),
                "frameToken", RuntimeValues.string(frame.frameToken()),
                "commandCount", RuntimeValues.integer(frame.commands().size()),
                "eventCount", RuntimeValues.integer(frame.events().size()));
        return inspectable("gameplay.frame", FRAME_TYPE, "Gameplay frame", values);
    }

    private InspectableEntity gameplayEntity(
            GameplayRuntimeFrame frame, EntitySnapshot entity) {
        TreeMap<String, RuntimeValue> values = new TreeMap<>();
        values.put("tick", RuntimeValues.integer(frame.world().tick()));
        values.put("frameToken", RuntimeValues.string(frame.frameToken()));
        values.put("lifecycle.state", RuntimeValues.enumValue(entity.state().name()));
        entity.components().forEach((type, component) ->
                projections.find(type).ifPresent(projection ->
                        merge(values, project(projection, entity, component), type.id())));
        requirePropertyLimit(values);
        return inspectable("gameplay.entity." + entity.id().value(), ENTITY_TYPE,
                entity.id().value(), values);
    }

    private InspectableEntity visualEntity(
            GameplayRuntimeFrame frame,
            EntitySnapshot entity,
            WorldVisualEntry visual) {
        TreeMap<String, RuntimeValue> values = new TreeMap<>();
        values.put("tick", RuntimeValues.integer(frame.world().tick()));
        values.put("frameToken", RuntimeValues.string(frame.frameToken()));
        if (visual == null) {
            values.put("status", RuntimeValues.enumValue("UNAVAILABLE"));
        } else {
            values.put("status", RuntimeValues.enumValue(visual.status().name()));
            values.put("asset", RuntimeValues.string(visual.asset()));
            values.put("region", RuntimeValues.string(visual.region()));
            values.put("worldPosition", vector(visual.worldPosition().x(),
                    visual.worldPosition().y()));
            values.put("spriteBounds", bounds(
                    visual.spriteBounds().minX(), visual.spriteBounds().minY(),
                    visual.spriteBounds().maxX(), visual.spriteBounds().maxY()));
            values.put("screenBounds", visual.screenBounds()
                    .<RuntimeValue>map(bounds -> bounds(
                            bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY()))
                    .orElseGet(RuntimeValues::nullValue));
            values.put("pivot", vector(visual.pivot().x(), visual.pivot().y()));
            values.put("rotation", RuntimeValues.decimal(visual.rotationRadians()));
            values.put("visible", RuntimeValues.bool(visual.visible()));
            values.put("cameraVisible", RuntimeValues.bool(visual.cameraVisible()));
            values.put("renderLayer", RuntimeValues.enumValue(visual.renderLayer()));
            values.put("renderOrder", RuntimeValues.integer(visual.renderOrder()));
        }
        requirePropertyLimit(values);
        return inspectable("gameplay.visual." + entity.id().value(), VISUAL_TYPE,
                entity.id().value() + " visual", values);
    }

    private void emitEvents(List<EventEnvelope> events) {
        Map<io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId, Integer>
                damageCounts = new HashMap<>();
        events.stream().map(EventEnvelope::event)
                .filter(DamageApplied.class::isInstance)
                .map(DamageApplied.class::cast)
                .forEach(damage -> damageCounts.merge(damage.subject(), 1, Integer::sum));
        Set<CauseKey> caused = new HashSet<>();
        for (EventEnvelope envelope : events) {
            EventSpec spec = eventSpec(envelope.event());
            envelope.attributes().values().forEach(
                    (name, value) -> spec.attribute(name, runtimeValue(value)));
            Optional<EventId> emitted = runtime.emit(spec);
            if (emitted.isPresent() && envelope.event() instanceof DamageApplied damage
                    && damageCounts.getOrDefault(damage.subject(), 0) == 1) {
                cause(emitted.get(), damage.subject(), "health.current", caused);
            }
            if (emitted.isPresent() && envelope.event() instanceof EntityKilled killed) {
                cause(emitted.get(), killed.subject(), "health.alive", caused);
            }
        }
    }

    private void cause(
            EventId eventId,
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId entityId,
            String property,
            Set<CauseKey> caused) {
        CauseKey key = new CauseKey(entityId, property);
        if (caused.add(key)) {
            runtime.causeNextChange(runtimeEntityId("gameplay.entity." + entityId.value()),
                    property, ChangeCause.event(eventId));
        }
    }

    private static EventSpec eventSpec(GameplayEvent event) {
        if (event instanceof EntitySpawned spawned) {
            return EventSpec.type("gameplay.entity-spawned")
                    .subject(runtimeEntityId("gameplay.entity." + spawned.subject().value()));
        }
        if (event instanceof EntityDespawned despawned) {
            return EventSpec.type("gameplay.entity-despawned")
                    .subject(runtimeEntityId("gameplay.entity." + despawned.subject().value()));
        }
        if (event instanceof DamageApplied damage) {
            return EventSpec.type("gameplay.damage-applied")
                    .subject(runtimeEntityId("gameplay.entity." + damage.subject().value()))
                    .source(runtimeEntityId("gameplay.entity." + damage.source().value()))
                    .attribute("amount", RuntimeValues.integer(damage.amount()));
        }
        if (event instanceof EntityKilled killed) {
            return EventSpec.type("gameplay.entity-killed")
                    .subject(runtimeEntityId("gameplay.entity." + killed.subject().value()))
                    .source(runtimeEntityId("gameplay.entity." + killed.source().value()));
        }
        if (event instanceof ItemCollected collected) {
            return EventSpec.type("gameplay.item-collected")
                    .subject(runtimeEntityId("gameplay.entity." + collected.subject().value()))
                    .source(runtimeEntityId("gameplay.entity." + collected.item().value()));
        }
        if (event instanceof ProjectileCreated projectile) {
            return EventSpec.type("gameplay.projectile-created")
                    .subject(runtimeEntityId("gameplay.entity." + projectile.subject().value()))
                    .source(runtimeEntityId("gameplay.entity." + projectile.source().value()));
        }
        if (event instanceof ObjectiveCompleted objective) {
            return EventSpec.type("gameplay.objective-completed")
                    .subject(runtimeEntityId("gameplay.entity." + objective.subject().value()))
                    .attribute("objectiveId", RuntimeValues.string(objective.objectiveId()));
        }
        throw GameplayException.validation(
                GameplayDiagnosticCode.UNKNOWN_RUNTIME_PROJECTION,
                "project-gameplay-event",
                "standard gameplay event",
                event.getClass().getName(),
                "Register only the closed V1 event vocabulary.");
    }

    private static RuntimeValue runtimeValue(EventAttributeValue value) {
        if (value instanceof EventAttributeValue.StringValue string) {
            return RuntimeValues.string(string.value());
        }
        if (value instanceof EventAttributeValue.IntegerValue integer) {
            return RuntimeValues.integer(integer.value());
        }
        if (value instanceof EventAttributeValue.DecimalValue decimal) {
            return RuntimeValues.decimal(decimal.value());
        }
        if (value instanceof EventAttributeValue.BooleanValue bool) {
            return RuntimeValues.bool(bool.value());
        }
        if (value instanceof EventAttributeValue.EntityValue entity) {
            return RuntimeValues.string(entity.value().value());
        }
        throw GameplayException.validation(
                GameplayDiagnosticCode.UNKNOWN_RUNTIME_PROJECTION,
                "project-event-attribute",
                "closed typed gameplay attribute",
                value.getClass().getName(),
                "Use a standard EventAttributeValue.");
    }

    private static RuntimeValue bounds(
            double minX, double minY, double maxX, double maxY) {
        return RuntimeValues.object(
                RuntimeValues.field("minX", RuntimeValues.decimal(minX)),
                RuntimeValues.field("minY", RuntimeValues.decimal(minY)),
                RuntimeValues.field("maxX", RuntimeValues.decimal(maxX)),
                RuntimeValues.field("maxY", RuntimeValues.decimal(maxY)));
    }

    private static RuntimeValue vector(double x, double y) {
        return RuntimeValues.vector2(x, y);
    }

    private static InspectableEntity inspectable(
            String id,
            EntityType type,
            String displayName,
            Map<String, RuntimeValue> values) {
        Map<String, RuntimeValue> immutable =
                Collections.unmodifiableMap(new TreeMap<>(values));
        return InspectableEntity.of(runtimeEntityId(id), type, () -> displayName,
                inspector -> immutable.forEach(
                        (name, value) -> inspector.property(name, () -> value)));
    }

    private void requirePropertyLimit(Map<String, RuntimeValue> values) {
        int maximum = runtime.configuration().limits().propertiesPerEntity();
        if (values.size() > maximum) {
            throw incomplete("at most " + maximum + " runtime properties per entity",
                    Integer.toString(values.size()),
                    "Reduce explicitly registered projections.");
        }
    }

    private static void merge(
            Map<String, RuntimeValue> target,
            Map<String, RuntimeValue> additions,
            String componentType) {
        additions.forEach((name, value) -> {
            if (target.putIfAbsent(
                    Objects.requireNonNull(name, "runtime property"),
                    Objects.requireNonNull(value, "runtime value")) != null) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.DUPLICATE_RUNTIME_PROJECTION,
                        "project-gameplay-entity",
                        "unique runtime property names",
                        componentType + ":" + name,
                        "Namespace every projected property by its stable component type.");
            }
        });
    }

    private static <T extends Component> Map<String, RuntimeValue> projectTyped(
            RuntimeProjection<T> projection,
            EntitySnapshot entity,
            Component component) {
        return projection.project(entity,
                projection.componentType().valueClass().cast(component));
    }

    private static Map<String, RuntimeValue> project(
            RuntimeProjection<?> projection,
            EntitySnapshot entity,
            Component component) {
        return projectTyped(castProjection(projection), entity, component);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> RuntimeProjection<T> castProjection(
            RuntimeProjection<?> projection) {
        return (RuntimeProjection<T>) projection;
    }

    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId runtimeEntityId(
            String value) {
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }

    private void requireOpen() {
        if (closed) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.RUNTIME_BRIDGE_CLOSED,
                    "use-gameplay-runtime",
                    "open gameplay runtime bridge",
                    "closed",
                    "Install a new bridge before capturing more frames.");
        }
    }

    private static GameplayException incomplete(
            String expected, String observed, String correction) {
        return GameplayException.validation(
                GameplayDiagnosticCode.RUNTIME_FRAME_INCOMPLETE,
                "capture-gameplay-runtime", expected, observed, correction);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (openTick >= 0) {
            runtime.endFrame();
            openTick = -1;
        }
        sourceRegistration.close();
        preparedVisuals = null;
        closed = true;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("unexpected checked capture failure", failure);
    }

    private record CauseKey(
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId entityId,
            String property) {
    }
}
