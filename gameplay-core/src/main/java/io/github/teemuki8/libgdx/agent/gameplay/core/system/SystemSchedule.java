package io.github.teemuki8.libgdx.agent.gameplay.core.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable compiled system order independent of registration order. */
public final class SystemSchedule {
    private static final Comparator<GameSystem> ORDER = Comparator
            .comparing((GameSystem system) -> system.descriptor().phase())
            .thenComparingInt(system -> system.descriptor().slot());

    private final List<GameSystem> systems;
    private final List<SystemDescriptor> descriptors;

    private SystemSchedule(List<GameSystem> systems) {
        this.systems = List.copyOf(systems);
        this.descriptors = systems.stream().map(GameSystem::descriptor).toList();
    }

    /** Validates and compiles a stable immutable schedule. */
    public static SystemSchedule compile(List<GameSystem> systems, GameplayLimits limits) {
        Objects.requireNonNull(systems, "systems");
        Objects.requireNonNull(limits, "limits");
        if (systems.size() > limits.maxSystems()) {
            throw failure(GameplayDiagnosticCode.SYSTEM_LIMIT_EXCEEDED,
                    "at most " + limits.maxSystems() + " systems",
                    Integer.toString(systems.size()),
                    "Reduce the explicit system catalog before compiling it.");
        }
        Set<String> ids = new HashSet<>();
        Set<PhaseSlot> slots = new HashSet<>();
        for (GameSystem system : systems) {
            Objects.requireNonNull(system, "system");
            SystemDescriptor descriptor = Objects.requireNonNull(
                    system.descriptor(), "system.descriptor");
            if (!ids.add(descriptor.id().value())) {
                throw failure(GameplayDiagnosticCode.DUPLICATE_SYSTEM_ID,
                        "unique system ID", descriptor.id().value(),
                        "Give every system one stable distinct identity.");
            }
            PhaseSlot phaseSlot = new PhaseSlot(descriptor.phase(), descriptor.slot());
            if (!slots.add(phaseSlot)) {
                throw failure(GameplayDiagnosticCode.DUPLICATE_SYSTEM_SLOT,
                        "unique phase and slot", phaseSlot.toString(),
                        "Assign one explicit free slot; registration order is not a tie-breaker.");
            }
        }
        return new SystemSchedule(systems.stream().sorted(ORDER).toList());
    }

    /** Returns immutable descriptors in execution order. */
    public List<SystemDescriptor> descriptors() {
        return descriptors;
    }

    /** Returns immutable systems in execution order. */
    public List<GameSystem> systems() {
        return systems;
    }

    private static GameplayException failure(
            GameplayDiagnosticCode code, String expected, String observed, String correction) {
        return GameplayException.validation(code, "compile-system-schedule",
                expected, observed, correction);
    }

    private record PhaseSlot(SystemPhase phase, int slot) {
    }
}
