package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GameplayRendererTest {
    @Test
    void drawOrderIsLayerThenOrderThenEntityId() {
        WorldSnapshot snapshot = new WorldSnapshot(2, List.of(
                drawable("z", "world", 0),
                drawable("a", "world", 0),
                drawable("b", "background", 9)));

        assertEquals(List.of("b", "a", "z"),
                GameplayRenderer.orderedEntities(snapshot).stream()
                        .map(entity -> entity.id().value()).toList());
    }

    private static EntitySnapshot drawable(String id, String layer, int order) {
        Map<ComponentType<?>, Component> components = Map.of(
                Transform2D.TYPE, new Transform2D(
                        Vec2.ZERO, 0, new Vec2(1, 1), new Vec2(0.5, 0.5)),
                Sprite.TYPE, new Sprite("sprite", "sprite",
                        new Vec2(1, 1), new Vec2(0.5, 0.5)),
                Render.TYPE, new Render(layer, order, Rgba.WHITE, true));
        return new EntitySnapshot(EntityId.of(id), EntityState.ACTIVE, components);
    }
}
