package io.github.teemuki8.libgdx.agent.gameplay.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AttachedTo;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import org.junit.jupiter.api.Test;

final class AttachedToResolutionTest {
    private static final EntityId PARENT = EntityId.of("parent");
    private static final EntityId CHILD = EntityId.of("child");
    private static final EntityId GRANDCHILD = EntityId.of("grandchild");
    private static final double EPSILON = 1e-9;

    @Test
    void childPoseIsTheParentsResolvedPoseComposedWithTheLocalPose() {
        QuaternionValue parentRotation =
                QuaternionValue.fromAxisAngle(new Vec3(0, 1, 0), Math.PI / 2);
        Transform3D local = new Transform3D(new Vec3(1, 0, 0),
                QuaternionValue.fromAxisAngle(new Vec3(0, 0, 1), Math.PI / 2), new Vec3(2, 2, 2));
        try (GameWorld world = GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> {
                    sink.spawn(EntityDraft.builder(PARENT)
                            .with(Transform3D.TYPE, new Transform3D(new Vec3(0, 3, 0), parentRotation))
                            .build());
                    sink.spawn(EntityDraft.builder(CHILD)
                            .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                            .with(AttachedTo.TYPE, new AttachedTo(PARENT, local))
                            .build());
                })
                .build()) {
            Transform3D derived = world.step().snapshot().entity(CHILD).orElseThrow()
                    .component(Transform3D.TYPE).orElseThrow();

            // Parent position (0,3,0) plus the local position (1,0,0) rotated by +90 degrees
            // about Y: (1,0,0) -> (0,0,-1).
            assertEquals(0, derived.position().x(), EPSILON);
            assertEquals(3, derived.position().y(), EPSILON);
            assertEquals(-1, derived.position().z(), EPSILON);
            assertEquals(parentRotation.multiply(local.rotation()), derived.rotation());
            assertEquals(local.scale(), derived.scale());
        }
    }

    @Test
    void chainsResolveParentFirstAndFollowTheMovingParent() {
        try (GameWorld world = GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> {
                    sink.spawn(EntityDraft.builder(PARENT)
                            .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                            .build());
                    sink.spawn(EntityDraft.builder(CHILD)
                            .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                            .with(AttachedTo.TYPE, new AttachedTo(PARENT,
                                    new Transform3D(new Vec3(1, 0, 0), QuaternionValue.IDENTITY)))
                            .build());
                    sink.spawn(EntityDraft.builder(GRANDCHILD)
                            .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                            .with(AttachedTo.TYPE, new AttachedTo(CHILD,
                                    new Transform3D(new Vec3(0, 0, 2), QuaternionValue.IDENTITY)))
                            .build());
                })
                .system(system("mover", SystemPhase.GAMEPLAY, 10, context -> context.replace(PARENT,
                        Transform3D.TYPE,
                        new Transform3D(new Vec3(5, 0, 0), QuaternionValue.IDENTITY))))
                .build()) {
            Transform3D grandchild = world.step().snapshot().entity(GRANDCHILD).orElseThrow()
                    .component(Transform3D.TYPE).orElseThrow();
            assertEquals(6, grandchild.position().x(), EPSILON);
            assertEquals(0, grandchild.position().y(), EPSILON);
            assertEquals(2, grandchild.position().z(), EPSILON);
        }
    }

    @Test
    void missingParentFreezesTheLastResolvedPose() {
        try (GameWorld world = GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> {
                    sink.spawn(EntityDraft.builder(PARENT)
                            .with(Transform3D.TYPE, new Transform3D(new Vec3(1, 2, 3), QuaternionValue.IDENTITY))
                            .build());
                    sink.spawn(EntityDraft.builder(CHILD)
                            .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                            .with(AttachedTo.TYPE, new AttachedTo(PARENT,
                                    new Transform3D(new Vec3(0, 0, 0), QuaternionValue.IDENTITY)))
                            .build());
                })
                .system(system("despawner", SystemPhase.GAMEPLAY, 20,
                        context -> { if (context.tick() == 1) context.despawn(PARENT); }))
                .build()) {
            world.step();
            Transform3D attached = world.step().snapshot().entity(CHILD).orElseThrow()
                    .component(Transform3D.TYPE).orElseThrow();
            assertEquals(1, attached.position().x(), EPSILON);
            assertEquals(2, attached.position().y(), EPSILON);
            assertEquals(3, attached.position().z(), EPSILON);
        }
    }

    @Test
    void selfAttachmentIsRejected() {
        try (GameWorld world = GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(EntityDraft.builder(CHILD)
                        .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                        .with(AttachedTo.TYPE, new AttachedTo(CHILD,
                                new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY)))
                        .build()))
                .build()) {
            GameplayException failure = assertThrows(GameplayException.class, world::step);
            assertEquals(GameplayDiagnosticCode.INVALID_ATTACHMENT, failure.code());
        }
    }

    private static GameSystem system(
            String id, SystemPhase phase, int slot, java.util.function.Consumer<SystemContext> body) {
        return new GameSystem() {
            @Override public SystemDescriptor descriptor() {
                return new SystemDescriptor(SystemId.of(id), phase, slot);
            }

            @Override public void update(SystemContext context) {
                body.accept(context);
            }
        };
    }
}
