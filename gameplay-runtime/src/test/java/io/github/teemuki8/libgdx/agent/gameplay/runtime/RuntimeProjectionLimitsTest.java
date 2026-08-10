package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import org.junit.jupiter.api.Test;

final class RuntimeProjectionLimitsTest {
    @Test
    void duplicateComponentProjectionIsRejectedTransactionally() {
        RuntimeProjection<Health> projection = StandardRuntimeProjections.registry()
                .require(Health.TYPE);
        RuntimeProjectionRegistry.Builder builder = RuntimeProjectionRegistry.builder()
                .register(projection);

        GameplayException failure = assertThrows(GameplayException.class,
                () -> builder.register(projection));

        assertEquals(GameplayDiagnosticCode.DUPLICATE_RUNTIME_PROJECTION, failure.code());
    }
}
