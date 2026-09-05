package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Presentation-only poses over an unchanged authoritative snapshot; never a world input. */
public final class PresentationFrame {
    private final WorldSnapshot current;
    private final Map<EntityId, Transform2D> poses;

    private PresentationFrame(WorldSnapshot current, Map<EntityId, Transform2D> poses) {
        this.current = Objects.requireNonNull(current, "current");
        this.poses = Map.copyOf(poses);
    }

    /** Uses the current pose directly, including the first frame and application discontinuities. */
    public static PresentationFrame current(WorldSnapshot snapshot) {
        return new PresentationFrame(snapshot, Map.of());
    }

    /**
     * Interpolates position and shortest-arc rotation between consecutive completed ticks.
     * New entities use current poses; removed entities remain absent. The application must use
     * {@link #current(WorldSnapshot)} after reset, teleport or same-ID entity replacement.
     */
    public static PresentationFrame between(WorldSnapshot previous, WorldSnapshot current, double alpha) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1
                || previous.entities().size() > io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits.ENTITY_MAXIMUM
                || current.entities().size() > io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits.ENTITY_MAXIMUM
                || current.tick() <= previous.tick() || current.tick() - previous.tick() != 1) {
            throw new IllegalArgumentException("interpolation requires consecutive ticks and alpha in [0,1]");
        }
        Map<EntityId, Transform2D> oldPoses = new TreeMap<>();
        previous.entities().forEach(entity -> entity.component(Transform2D.TYPE)
                .ifPresent(pose -> oldPoses.put(entity.id(), pose)));
        Map<EntityId, Transform2D> poses = new TreeMap<>();
        for (EntitySnapshot entity : current.entities()) {
            Transform2D prior = oldPoses.get(entity.id());
            Transform2D next = entity.component(Transform2D.TYPE).orElse(null);
            if (prior != null && next != null) {
                double angleDelta = Math.IEEEremainder(
                        Math.IEEEremainder(next.rotationRadians(), Math.PI * 2)
                                - Math.IEEEremainder(prior.rotationRadians(), Math.PI * 2),
                        Math.PI * 2);
                poses.put(entity.id(), new Transform2D(new Vec2(
                        prior.position().x() * (1 - alpha) + next.position().x() * alpha,
                        prior.position().y() * (1 - alpha) + next.position().y() * alpha),
                        prior.rotationRadians() + angleDelta * alpha, next.size(), next.pivot()));
            }
        }
        return new PresentationFrame(current, poses);
    }

    /** Returns the original authoritative snapshot, without presentation changes. */
    public WorldSnapshot current() {
        return current;
    }

    /** Returns the selected visual pose for a drawable entity from the current snapshot. */
    public Transform2D transform(EntitySnapshot entity) {
        Objects.requireNonNull(entity, "entity");
        return poses.getOrDefault(entity.id(), entity.component(Transform2D.TYPE).orElseThrow());
    }
}
