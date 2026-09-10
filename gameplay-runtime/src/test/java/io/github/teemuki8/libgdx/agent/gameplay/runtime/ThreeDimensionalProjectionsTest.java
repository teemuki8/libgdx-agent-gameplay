package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Aim3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Velocity3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ThreeDimensionalProjectionsTest {
    @Test
    void projectsImmutablePoseVelocityAndViewWithoutWidgetOrNativeAuthority() {
        var transform = new Transform3D(new Vec3(1, 2, 3), QuaternionValue.IDENTITY, new Vec3(2, 3, 4));
        var velocity = new Velocity3D(new Vec3(4, 5, 6));
        var aim = new Aim3D(0, 0);
        var entity = new EntitySnapshot(EntityId.of("player"), EntityState.ACTIVE,
                Map.of(Transform3D.TYPE, transform, Velocity3D.TYPE, velocity, Aim3D.TYPE, aim));
        var projections = StandardRuntimeProjections.registry();
        var poseValues = projections.require(Transform3D.TYPE).project(entity, transform);
        assertEquals(3, poseValues.size());
        assertEquals(RuntimeValues.object(RuntimeValues.field("x", RuntimeValues.decimal(1)),
                RuntimeValues.field("y", RuntimeValues.decimal(2)), RuntimeValues.field("z", RuntimeValues.decimal(3))),
                poseValues.get("transform3d.position"));
        assertEquals(RuntimeValues.object(RuntimeValues.field("x", RuntimeValues.decimal(0)),
                RuntimeValues.field("y", RuntimeValues.decimal(0)), RuntimeValues.field("z", RuntimeValues.decimal(0)),
                RuntimeValues.field("w", RuntimeValues.decimal(1))), poseValues.get("transform3d.rotation"));
        assertEquals(RuntimeValues.object(RuntimeValues.field("x", RuntimeValues.decimal(2)),
                RuntimeValues.field("y", RuntimeValues.decimal(3)), RuntimeValues.field("z", RuntimeValues.decimal(4))),
                poseValues.get("transform3d.scale"));
        assertEquals(RuntimeValues.object(RuntimeValues.field("x", RuntimeValues.decimal(4)),
                RuntimeValues.field("y", RuntimeValues.decimal(5)), RuntimeValues.field("z", RuntimeValues.decimal(6))),
                projections.require(Velocity3D.TYPE).project(entity, velocity).get("velocity3d.linear"));
        var viewValues = projections.require(Aim3D.TYPE).project(entity, aim);
        assertEquals(RuntimeValues.decimal(0), viewValues.get("aim3d.yawRadians"));
        assertEquals(RuntimeValues.decimal(0), viewValues.get("aim3d.pitchRadians"));
        assertEquals(RuntimeValues.object(RuntimeValues.field("x", RuntimeValues.decimal(0)),
                RuntimeValues.field("y", RuntimeValues.decimal(0)), RuntimeValues.field("z", RuntimeValues.decimal(-1))),
                viewValues.get("aim3d.direction"));
        assertThrows(UnsupportedOperationException.class, poseValues::clear);
    }
}
