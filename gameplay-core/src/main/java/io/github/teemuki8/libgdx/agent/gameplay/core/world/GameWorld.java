package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandBuffer;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentRegistry;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntityDespawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EntitySpawned;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventBuffer;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemSchedule;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Owner-thread deterministic gameplay world with staged structural lifecycle. */
public final class GameWorld implements AutoCloseable {
    private final GameplayLimits limits;
    private final ComponentRegistry componentRegistry;
    private final long fixedStepNanos;
    private final SystemSchedule schedule;
    private final List<ParticipantRegistration> participants;
    private final List<ParticipantRegistration> disposalOrder;
    private final WorldInitializer initializer;
    private final Thread ownerThread;
    private final CommandBuffer commandBuffer;
    private final EventBuffer eventBuffer;
    private final Map<EntityId, EntityRecord> active = new TreeMap<>();
    private final Map<EntityId, EntityDraft> pendingSpawn = new LinkedHashMap<>();
    private final Set<EntityId> pendingDespawn = new LinkedHashSet<>();
    private final List<EntityRecord> pendingDisposal = new ArrayList<>();

    private volatile WorldSnapshot latestSnapshot = new WorldSnapshot(0, List.of());
    private long tick;
    private boolean stepping;
    private boolean resetRequested;
    private boolean closed;
    private List<CommandEnvelope> currentCommands = List.of();

    private GameWorld(Builder builder) {
        limits = builder.limits;
        componentRegistry = builder.componentRegistry;
        fixedStepNanos = builder.fixedStepNanos;
        schedule = SystemSchedule.compile(builder.systems, limits);
        participants = registrations(builder.participants);
        disposalOrder = participants.stream()
                .sorted(Comparator.comparingInt(ParticipantRegistration::dependencyLevel)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(
                                ParticipantRegistration::registrationIndex).reversed()))
                .toList();
        initializer = builder.initializer;
        ownerThread = Thread.currentThread();
        commandBuffer = new CommandBuffer(limits);
        eventBuffer = new EventBuffer(limits);
        initializer.initialize(this::spawnInternal);
    }

    /** Starts an owner-thread world builder with explicit limits and component registry. */
    public static Builder builder(GameplayLimits limits, ComponentRegistry components) {
        return new Builder(limits, components);
    }

    /** Returns the next tick that will execute. */
    public long tick() {
        requireOwner("read-world-tick");
        requireOpen("read-world-tick");
        return tick;
    }

    /** Returns the immutable compiled schedule. */
    public SystemSchedule schedule() {
        return schedule;
    }

    /** Resolves one currently active entity on the owner thread. */
    public Optional<EntityView> entity(EntityId id) {
        requireOwner("read-entity");
        requireOpen("read-entity");
        EntityRecord record = active.get(Objects.requireNonNull(id, "id"));
        return record == null ? Optional.empty() : Optional.of(record.view(EntityState.ACTIVE));
    }

    /** Queries active entities in stable semantic-ID order. */
    public List<EntityView> query(ComponentType<?>... required) {
        requireOwner("query-world");
        requireOpen("query-world");
        ComponentType<?>[] copied = required.clone();
        return queryInternal(copied);
    }

    /** Queues one command for deterministic future consumption. */
    public void enqueue(CommandEnvelope command) {
        requireOwner("enqueue-command");
        requireOpen("enqueue-command");
        commandBuffer.enqueue(command);
    }

    /** Queues a detached entity draft for the next activation barrier. */
    public void spawn(EntityDraft draft) {
        requireOwner("spawn-entity");
        requireOpen("spawn-entity");
        spawnInternal(draft);
    }

    /** Queues logical removal at the next post-gameplay barrier. */
    public void despawn(EntityId id) {
        requireOwner("despawn-entity");
        requireOpen("despawn-entity");
        EntityId checked = Objects.requireNonNull(id, "id");
        if (!active.containsKey(checked)) {
            throw failure(GameplayDiagnosticCode.UNKNOWN_ENTITY,
                    "despawn-entity", "an active entity", checked.value(),
                    "Resolve a fresh entity view before requesting despawn.");
        }
        pendingDespawn.add(checked);
    }

    /** Requests a deterministic reset after the current tick's disposal barrier. */
    public void requestReset() {
        requireOwner("request-reset");
        requireOpen("request-reset");
        resetRequested = true;
    }

    /** Advances exactly one fixed simulation tick. */
    public CompletedTick step() {
        requireOwner("step-world");
        requireOpen("step-world");
        if (stepping) {
            throw failure(GameplayDiagnosticCode.WORLD_ALREADY_STEPPING,
                    "step-world", "no active step", "nested step",
                    "Return from the current system callback before advancing again.");
        }
        stepping = true;
        try {
            commandBuffer.advanceTo(tick);
            currentCommands = commandBuffer.commandsFor(tick);
            eventBuffer.openTick(tick);
            activatePending();
            for (SystemPhase phase : SystemPhase.values()) {
                runPhase(phase);
                if (phase == SystemPhase.GAMEPLAY) {
                    logicallyDespawnPending();
                }
            }
            disposePending();
            List<EventEnvelope> completedEvents = eventBuffer.closeTick();
            WorldSnapshot completedSnapshot = snapshotInternal(tick);
            CompletedTick completed = new CompletedTick(
                    completedSnapshot, currentCommands, completedEvents);
            latestSnapshot = completedSnapshot;
            tick++;
            currentCommands = List.of();
            if (resetRequested) {
                resetInternal();
            }
            return completed;
        } finally {
            stepping = false;
        }
    }

    /** Returns the latest completed immutable snapshot from any thread. */
    public WorldSnapshot snapshot() {
        return latestSnapshot;
    }

    /** Disposes active bridge-owned mappings without closing caller-owned resources. */
    @Override
    public void close() {
        requireOwner("close-world");
        if (closed) {
            return;
        }
        active.values().forEach(record -> {
            EntityView view = record.view(EntityState.LOGICALLY_REMOVED);
            participants.forEach(registration -> registration.participant()
                    .onLogicalDespawn(view));
            pendingDisposal.add(record);
        });
        active.clear();
        pendingSpawn.clear();
        pendingDespawn.clear();
        disposePending();
        disposalOrder.forEach(registration -> registration.participant().onClose());
        commandBuffer.reset();
        eventBuffer.reset();
        closed = true;
    }

    private void runPhase(SystemPhase phase) {
        for (GameSystem system : schedule.systems()) {
            if (system.descriptor().phase() == phase) {
                Context context = new Context(phase);
                try {
                    system.update(context);
                } finally {
                    context.invalidate();
                }
            }
        }
    }

    private void activatePending() {
        if (pendingSpawn.isEmpty()) {
            return;
        }
        List<EntityDraft> drafts = List.copyOf(pendingSpawn.values());
        pendingSpawn.clear();
        for (EntityDraft draft : drafts) {
            EntityRecord record = new EntityRecord(draft.id(), draft.components());
            active.put(record.id(), record);
            EntityView view = record.view(EntityState.ACTIVE);
            participants.forEach(registration -> registration.participant().onActivate(view));
            eventBuffer.emit(new EntitySpawned(record.id()));
        }
    }

    private void logicallyDespawnPending() {
        if (pendingDespawn.isEmpty()) {
            return;
        }
        List<EntityId> ids = pendingDespawn.stream().sorted().toList();
        pendingDespawn.clear();
        for (EntityId id : ids) {
            EntityRecord record = active.remove(id);
            if (record != null) {
                EntityView view = record.view(EntityState.LOGICALLY_REMOVED);
                participants.forEach(registration -> registration.participant()
                        .onLogicalDespawn(view));
                eventBuffer.emit(new EntityDespawned(id));
                pendingDisposal.add(record);
            }
        }
    }

    private void disposePending() {
        pendingDisposal.sort(Comparator.comparing(EntityRecord::id));
        for (EntityRecord record : pendingDisposal) {
            disposalOrder.forEach(registration -> registration.participant()
                    .onDispose(record.id()));
        }
        pendingDisposal.clear();
    }

    private void resetInternal() {
        active.values().forEach(record -> {
            EntityView view = record.view(EntityState.LOGICALLY_REMOVED);
            participants.forEach(registration -> registration.participant()
                    .onLogicalDespawn(view));
            pendingDisposal.add(record);
        });
        active.clear();
        pendingSpawn.clear();
        pendingDespawn.clear();
        disposePending();
        commandBuffer.reset();
        eventBuffer.reset();
        participants.forEach(registration -> registration.participant().onReset());
        tick = 0;
        latestSnapshot = new WorldSnapshot(0, List.of());
        resetRequested = false;
        initializer.initialize(this::spawnInternal);
    }

    private void spawnInternal(EntityDraft draft) {
        EntityDraft checked = Objects.requireNonNull(draft, "draft");
        if (active.containsKey(checked.id()) || pendingSpawn.containsKey(checked.id())
                || pendingDisposal.stream().anyMatch(record -> record.id().equals(checked.id()))) {
            throw failure(GameplayDiagnosticCode.DUPLICATE_ENTITY_ID,
                    "spawn-entity", "unique entity ID", checked.id().value(),
                    "Choose a fresh semantic ID or wait for disposal before reuse.");
        }
        if (active.size() + pendingSpawn.size() >= limits.maxEntities()) {
            throw failure(GameplayDiagnosticCode.ENTITY_LIMIT_EXCEEDED,
                    "spawn-entity", "at most " + limits.maxEntities() + " entities",
                    Integer.toString(active.size() + pendingSpawn.size() + 1),
                    "Despawn an entity or lower spawn production before retrying.");
        }
        if (checked.components().size() > limits.maxComponentsPerEntity()) {
            throw failure(GameplayDiagnosticCode.COMPONENT_LIMIT_EXCEEDED,
                    "spawn-entity",
                    "at most " + limits.maxComponentsPerEntity() + " components",
                    Integer.toString(checked.components().size()),
                    "Use the bounded standard component vocabulary.");
        }
        checked.components().forEach((type, value) -> validateComponent(type, value));
        pendingSpawn.put(checked.id(), checked);
    }

    private void validateComponent(ComponentType<?> type, Component value) {
        ComponentType<?> registered = componentRegistry.require(type.id());
        if (!registered.equals(type) || !type.valueClass().isInstance(value)) {
            throw failure(GameplayDiagnosticCode.COMPONENT_TYPE_MISMATCH,
                    "spawn-entity", registered.toString(), type + ":" + value.getClass().getName(),
                    "Use the exact registered ComponentType with its matching value class.");
        }
    }

    private List<EntityView> queryInternal(ComponentType<?>[] required) {
        for (ComponentType<?> type : required) {
            componentRegistry.require(Objects.requireNonNull(type, "requiredType").id());
        }
        return active.values().stream()
                .map(record -> record.view(EntityState.ACTIVE))
                .filter(view -> view.hasAll(required))
                .toList();
    }

    private WorldSnapshot snapshotInternal(long snapshotTick) {
        return new WorldSnapshot(snapshotTick, active.values().stream()
                .map(EntityRecord::snapshot)
                .toList());
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw failure(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    operation, "owner thread " + ownerThread.getName(),
                    Thread.currentThread().getName(),
                    "Schedule gameplay mutation on the thread that created the world.");
        }
    }

    private void requireOpen(String operation) {
        if (closed) {
            throw failure(GameplayDiagnosticCode.WORLD_CLOSED,
                    operation, "open world", "closed world",
                    "Create a new world instead of reusing a closed one.");
        }
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code,
            String operation,
            String expected,
            String observed,
            String correction) {
        return GameplayException.validation(code, operation, expected, observed, correction);
    }

    private static List<ParticipantRegistration> registrations(
            List<LifecycleParticipant> participants) {
        ArrayList<ParticipantRegistration> result = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            result.add(new ParticipantRegistration(participants.get(index), index));
        }
        return List.copyOf(result);
    }

    private final class Context implements SystemContext {
        private final SystemPhase phase;
        private boolean valid = true;

        private Context(SystemPhase phase) {
            this.phase = phase;
        }

        @Override
        public long tick() {
            requireValid();
            return GameWorld.this.tick;
        }

        @Override
        public long fixedStepNanos() {
            requireValid();
            return GameWorld.this.fixedStepNanos;
        }

        @Override
        public SystemPhase phase() {
            requireValid();
            return phase;
        }

        @Override
        public List<EntityView> query(ComponentType<?>... required) {
            requireValid();
            return queryInternal(required.clone());
        }

        @Override
        public <T extends Component> void replace(
                EntityId id, ComponentType<T> type, T value) {
            requireValid();
            if (phase == SystemPhase.RUNTIME_CAPTURE) {
                throw mutationFailure("replace component");
            }
            validateComponent(type, value);
            EntityRecord record = active.get(id);
            if (record == null || !record.components().containsKey(type)) {
                throw failure(GameplayDiagnosticCode.UNKNOWN_ENTITY,
                        "replace-component", "active entity with component " + type.id(),
                        String.valueOf(id),
                        "Query the active entity and replace only an existing component.");
            }
            record.replace(type, value);
        }

        @Override
        public List<CommandEnvelope> commands() {
            requireValid();
            return currentCommands;
        }

        @Override
        public void emit(GameplayEvent event) {
            requireValid();
            eventBuffer.emit(event);
        }

        @Override
        public void emit(GameplayEvent event, EventAttributes attributes) {
            requireValid();
            eventBuffer.emit(event, attributes);
        }

        @Override
        public void spawn(EntityDraft draft) {
            requireValid();
            requireStructuralPhase("spawn entity");
            spawnInternal(draft);
        }

        @Override
        public void despawn(EntityId id) {
            requireValid();
            requireStructuralPhase("despawn entity");
            GameWorld.this.despawn(id);
        }

        private void requireStructuralPhase(String mutation) {
            if (phase.compareTo(SystemPhase.GAMEPLAY) > 0) {
                throw mutationFailure(mutation);
            }
        }

        private GameplayException mutationFailure(String mutation) {
            return failure(GameplayDiagnosticCode.MUTATION_NOT_ALLOWED_IN_PHASE,
                    "mutate-world", "mutation no later than GAMEPLAY/ANIMATION",
                    mutation + " during " + phase,
                    "Move authoritative mutation into an earlier deterministic system phase.");
        }

        private void requireValid() {
            if (!valid) {
                throw failure(GameplayDiagnosticCode.MUTATION_NOT_ALLOWED_IN_PHASE,
                        "use-system-context", "active system callback", "expired context",
                        "Use SystemContext only during the callback that received it.");
            }
        }

        private void invalidate() {
            valid = false;
        }
    }

    private record ParticipantRegistration(
            LifecycleParticipant participant, int registrationIndex) {
        private int dependencyLevel() {
            return participant.dependencyLevel();
        }
    }

    private static final class EntityRecord {
        private final EntityId id;
        private final Map<ComponentType<?>, Component> components;

        private EntityRecord(EntityId id, Map<ComponentType<?>, Component> components) {
            this.id = id;
            this.components = new TreeMap<>(components);
        }

        private EntityId id() {
            return id;
        }

        private Map<ComponentType<?>, Component> components() {
            return components;
        }

        private EntityView view(EntityState state) {
            return new EntityView(id, state, components);
        }

        private EntitySnapshot snapshot() {
            return new EntitySnapshot(id, EntityState.ACTIVE, components);
        }

        private <T extends Component> void replace(ComponentType<T> type, T value) {
            components.put(type, value);
        }
    }

    /** Mutable world builder compiled into immutable schedules and participant order. */
    public static final class Builder {
        private final GameplayLimits limits;
        private final ComponentRegistry componentRegistry;
        private final List<GameSystem> systems = new ArrayList<>();
        private final List<LifecycleParticipant> participants = new ArrayList<>();
        private long fixedStepNanos = 16_666_667L;
        private WorldInitializer initializer = sink -> { };

        private Builder(GameplayLimits limits, ComponentRegistry componentRegistry) {
            this.limits = Objects.requireNonNull(limits, "limits");
            this.componentRegistry = Objects.requireNonNull(
                    componentRegistry, "componentRegistry");
        }

        /** Sets the positive fixed simulation duration. */
        public Builder fixedStepNanos(long value) {
            if (value < 1) {
                throw failure(GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                        "configure-world", "fixedStepNanos >= 1", Long.toString(value),
                        "Use a positive fixed simulation duration.");
            }
            fixedStepNanos = value;
            return this;
        }

        /** Registers one system before schedule compilation. */
        public Builder system(GameSystem system) {
            systems.add(Objects.requireNonNull(system, "system"));
            return this;
        }

        /** Registers one adapter lifecycle participant. */
        public Builder lifecycleParticipant(LifecycleParticipant participant) {
            participants.add(Objects.requireNonNull(participant, "participant"));
            return this;
        }

        /** Sets the precompiled initializer replayed after reset. */
        public Builder initializer(WorldInitializer value) {
            initializer = Objects.requireNonNull(value, "initializer");
            return this;
        }

        /** Compiles and creates the owner-thread world. */
        public GameWorld build() {
            return new GameWorld(this);
        }
    }
}
