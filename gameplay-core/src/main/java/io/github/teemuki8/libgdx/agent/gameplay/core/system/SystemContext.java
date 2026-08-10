package io.github.teemuki8.libgdx.agent.gameplay.core.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.GameplayEvent;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;

/** Phase-scoped view and mutation surface supplied to a game system. */
public interface SystemContext {
    /** Returns the current deterministic tick. */
    long tick();

    /** Returns the configured fixed duration. */
    long fixedStepNanos();

    /** Returns the phase of the currently executing system. */
    SystemPhase phase();

    /** Returns active entities containing every requested component type. */
    List<EntityView> query(ComponentType<?>... required);

    /** Replaces one existing component value without changing entity shape. */
    <T extends Component> void replace(EntityId id, ComponentType<T> type, T value);

    /** Returns the immutable commands targeted at this tick. */
    List<CommandEnvelope> commands();

    /** Queues ordered controller intent for a future tick from the INPUT phase. */
    void enqueue(CommandEnvelope command);

    /** Returns an immutable snapshot of authoritative state at this exact phase. */
    WorldSnapshot snapshot();

    /** Returns immutable gameplay events emitted so far in this tick. */
    List<EventEnvelope> events();

    /** Emits an event without attributes. */
    void emit(GameplayEvent event);

    /** Emits an event with bounded attributes. */
    void emit(GameplayEvent event, EventAttributes attributes);

    /** Queues a detached draft for the next activation barrier. */
    void spawn(EntityDraft draft);

    /** Queues logical removal at the post-gameplay barrier. */
    void despawn(EntityId id);
}
