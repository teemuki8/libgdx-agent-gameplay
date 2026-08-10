package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class FixedStepLoopTest {
    @Test
    void capsCatchUpWithoutDroppingDeterministicTickOrder() {
        FixedStepLoop loop = new FixedStepLoop(
                Duration.ofSeconds(1).dividedBy(60), 5);
        AtomicInteger ticks = new AtomicInteger();

        int advanced = loop.advance(Duration.ofMillis(100), ticks::incrementAndGet);

        assertEquals(5, advanced);
        assertEquals(5, ticks.get());
        assertTrue(loop.hasBacklog());
    }

    @Test
    void sustainedBacklogFailsInsteadOfDroppingSimulationTime() {
        FixedStepLoop loop = new FixedStepLoop(Duration.ofMillis(10), 1);
        loop.advance(Duration.ofMillis(250), () -> { });

        GameplayException failure = assertThrows(GameplayException.class,
                () -> loop.advance(Duration.ofMillis(250), () -> { }));

        assertEquals(GameplayDiagnosticCode.FRAME_BACKLOG_EXCEEDED, failure.code());
    }
}
