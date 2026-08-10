package io.github.teemuki8.libgdx.agent.gameplay.core.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SystemScheduleTest {
    @Test
    void sortsByPhaseAndSlotRatherThanRegistrationOrder() {
        GameSystem gameplay = system("combat", SystemPhase.GAMEPLAY, 20);
        GameSystem input = system("commands", SystemPhase.INPUT, 10);

        SystemSchedule schedule = SystemSchedule.compile(
                List.of(gameplay, input), GameplayLimits.defaults());

        assertEquals(List.of("commands", "combat"), schedule.descriptors().stream()
                .map(value -> value.id().value()).toList());
    }

    @Test
    void rejectsDuplicateIdsAndPhaseSlots() {
        assertCode(GameplayDiagnosticCode.DUPLICATE_SYSTEM_ID,
                () -> SystemSchedule.compile(List.of(
                        system("combat", SystemPhase.GAMEPLAY, 10),
                        system("combat", SystemPhase.ANIMATION, 20)),
                        GameplayLimits.defaults()));
        assertCode(GameplayDiagnosticCode.DUPLICATE_SYSTEM_SLOT,
                () -> SystemSchedule.compile(List.of(
                        system("movement", SystemPhase.GAMEPLAY, 20),
                        system("combat", SystemPhase.GAMEPLAY, 20)),
                        GameplayLimits.defaults()));
    }

    private static GameSystem system(String id, SystemPhase phase, int slot) {
        return new GameSystem() {
            @Override
            public SystemDescriptor descriptor() {
                return new SystemDescriptor(SystemId.of(id), phase, slot);
            }

            @Override
            public void update(SystemContext context) {
            }
        };
    }

    private static void assertCode(GameplayDiagnosticCode code, Runnable operation) {
        GameplayException failure = assertThrows(GameplayException.class, operation::run);
        assertEquals(code, failure.code());
    }
}
