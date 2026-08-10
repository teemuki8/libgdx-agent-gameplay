package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.UiFrameCorrelation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RuntimeCorrelationTest {
    @Test
    void bridgeNeverFabricatesUiCorrelationBeforeApplicationRendering() {
        AgentRuntime runtime = AgentRuntime.builder().build();
        try (GameplayRuntimeBridge bridge = new GameplayRuntimeBridge(
                runtime, StandardRuntimeProjections.registry(), GameplayLimits.defaults())) {
            runtime.start();
            try (var world = GameplayRuntimeBridgeTest.world(bridge)) {
                world.step();
            }

            assertTrue(runtime.uiCorrelations()
                    .framesForToken(bridge.lastFrameToken().orElseThrow(), 10)
                    .items().isEmpty());
            var completed = runtime.latestFrame().orElseThrow();
            runtime.uiCorrelations().recordFrame(new UiFrameCorrelation(
                    runtime.currentEpoch(), completed.frameId(), "arena",
                    Optional.of("1"), Optional.of(bridge.lastFrameToken().orElseThrow())));
            assertEquals(1, runtime.uiCorrelations()
                    .framesForToken(bridge.lastFrameToken().orElseThrow(), 10)
                    .items().size());
        }
        runtime.close();
    }
}
