package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityIdAllocator;
import org.junit.jupiter.api.Test;

final class ComponentRegistryTest {
    @Test
    void rejectsDuplicateStableTypeIds() {
        ComponentRegistry registry = ComponentRegistry.builder()
                .register(Health.TYPE)
                .build();

        assertEquals(Health.class, registry.require("health").valueClass());
        GameplayException failure = assertThrows(GameplayException.class,
                () -> ComponentRegistry.builder()
                        .register(Health.TYPE)
                        .register(new ComponentType<>("health", Faction.class))
                        .build());
        assertEquals(GameplayDiagnosticCode.DUPLICATE_COMPONENT_TYPE, failure.code());
    }

    @Test
    void failedRegistrationDoesNotPartiallyMutateTheBuilder() {
        ComponentRegistry.Builder builder = ComponentRegistry.builder().register(Health.TYPE);

        assertThrows(GameplayException.class,
                () -> builder.register(new ComponentType<>("hit-points", Health.class)));

        ComponentRegistry registry = builder.build();
        assertEquals(1, registry.types().size());
        assertThrows(GameplayException.class, () -> registry.require("hit-points"));
    }

    @Test
    void allocatorProducesSemanticIdsAndResetsExplicitly() {
        EntityIdAllocator allocator = new EntityIdAllocator("projectile", 4);

        assertEquals(EntityId.of("projectile-0001"), allocator.next());
        assertEquals(EntityId.of("projectile-0002"), allocator.next());
        allocator.reset();
        assertEquals(EntityId.of("projectile-0001"), allocator.next());
    }

    @Test
    void rejectsInvalidSemanticIdentifiers() {
        GameplayException failure = assertThrows(GameplayException.class,
                () -> EntityId.of("native pointer 0x1234"));
        assertEquals(GameplayDiagnosticCode.INVALID_IDENTIFIER, failure.code());
    }
}
