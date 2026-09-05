package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PresentationFrameTest {
    @Test
    void interpolationDoesNotChangeEitherAuthoritativeSnapshot() {
        EntitySnapshot oldEntity = entity(0, Math.toRadians(350));
        EntitySnapshot newEntity = entity(10, Math.toRadians(10));
        WorldSnapshot previous = new WorldSnapshot(5, List.of(oldEntity));
        WorldSnapshot current = new WorldSnapshot(6, List.of(newEntity));
        PresentationFrame frame = PresentationFrame.between(previous, current, 0.25);
        assertEquals(2.5, frame.transform(newEntity).position().x(), 0.00001);
        assertEquals(Math.toRadians(355), frame.transform(newEntity).rotationRadians(), 0.00001);
        assertEquals(0, oldEntity.component(Transform2D.TYPE).orElseThrow().position().x());
        assertEquals(10, newEntity.component(Transform2D.TYPE).orElseThrow().position().x());
    }

    @Test
    void newlySpawnedEntitiesUseCurrentPoseAndTickDiscontinuitiesRejectBlending() {
        EntitySnapshot spawned = entity(10, 0);
        WorldSnapshot current = new WorldSnapshot(6, List.of(spawned));
        PresentationFrame frame = PresentationFrame.between(new WorldSnapshot(5, List.of()), current, 0);
        assertEquals(10, frame.transform(spawned).position().x());
        assertThrows(IllegalArgumentException.class,
                () -> PresentationFrame.between(current, new WorldSnapshot(0, List.of()), 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> PresentationFrame.between(new WorldSnapshot(5, List.of()), current, Double.NaN));
    }

    private static EntitySnapshot entity(double x, double angle) {
        return new EntitySnapshot(EntityId.of("player"), EntityState.ACTIVE, Map.of(
                Transform2D.TYPE, new Transform2D(new Vec2(x, 2), angle,
                        new Vec2(1, 1), new Vec2(0.5, 0.5))));
    }
}
