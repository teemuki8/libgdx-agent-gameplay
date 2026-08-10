package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.PrefabId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class PrefabInstantiationTest {
    @Test
    void createsIndependentDraftsAndAllowsOnlyPoseOverride() {
        PrefabDefinition prefab = new PrefabParser(
                StandardComponentCodecs.registry(), PrefabLimits.defaults())
                .parse(JSON.getBytes(StandardCharsets.UTF_8))
                .require(PrefabId.of("player"));
        Transform2D override = new Transform2D(
                new Vec2(9, 7), 0.25, new Vec2(1, 1), new Vec2(0.5, 0.5));

        EntityDraft first = prefab.instantiate(EntityId.of("player-one"));
        EntityDraft second = prefab.instantiate(EntityId.of("player-two"), override);

        assertNotSame(first.components(), second.components());
        assertEquals(new Health(3, 3), first.components().get(Health.TYPE));
        assertEquals(override, second.components().get(Transform2D.TYPE));
        assertThrows(UnsupportedOperationException.class,
                () -> first.components().clear());
    }

    private static final String JSON = """
            {"schemaVersion":"gameplay-prefabs/1","prefabs":[
              {"id":"player","components":[
                {"type":"transform","position":[0,0],"size":[1,1],"pivot":[0.5,0.5]},
                {"type":"health","current":3,"max":3}
              ]}
            ]}
            """;
}
