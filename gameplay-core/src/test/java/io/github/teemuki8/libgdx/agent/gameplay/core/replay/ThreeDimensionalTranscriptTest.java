package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.Aim3DCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.Fire3DCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.Move3DCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Aim3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Velocity3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.ProjectileCreated;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ThreeDimensionalTranscriptTest {
    private static final EntityId PLAYER = EntityId.of("player");
    private static final CommandSourceId INPUT = CommandSourceId.of("input");

    @Test
    void existingWorldAndTranscriptReplayThreeDimensionalIntentInStableOrder() {
        var first = new TranscriptRunner(ThreeDimensionalTranscriptTest::world).run(transcript(0.5), 5);
        var same = new TranscriptRunner(ThreeDimensionalTranscriptTest::world).run(transcript(0.5), 5);
        var changed = new TranscriptRunner(ThreeDimensionalTranscriptTest::world).run(transcript(0.6), 5);
        assertEquals(first.tickDigests(), same.tickDigests());
        assertEquals(first.eventDigests(), same.eventDigests());
        assertEquals(first.tickDigests().subList(0, 2), changed.tickDigests().subList(0, 2));
        assertNotEquals(first.tickDigests().get(2), changed.tickDigests().get(2));
    }

    private static CommandTranscript transcript(double yaw) {
        return new CommandTranscript(List.of(
                new CommandEnvelope(3, INPUT, 3, new Fire3DCommand(PLAYER, Vec3.ZERO, new Vec3(0, 0, -1))),
                new CommandEnvelope(2, INPUT, 2, new Aim3DCommand(PLAYER, new Aim3D(yaw, 0))),
                new CommandEnvelope(1, INPUT, 1, new Move3DCommand(PLAYER, new Vec3(1, 0, 0)))));
    }

    private static GameWorld world() {
        return GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(EntityDraft.builder(PLAYER)
                        .with(Transform3D.TYPE, new Transform3D(Vec3.ZERO, QuaternionValue.IDENTITY))
                        .with(Velocity3D.TYPE, new Velocity3D(Vec3.ZERO))
                        .with(Aim3D.TYPE, new Aim3D(0, 0)).build()))
                .system(new IntentSystem()).build();
    }

    private static final class IntentSystem implements GameSystem {
        @Override
        public SystemDescriptor descriptor() {
            return new SystemDescriptor(SystemId.of("intent3d"), SystemPhase.INPUT, 10);
        }

        @Override
        public void update(SystemContext context) {
            for (var envelope : context.commands()) {
                if (envelope.command() instanceof Move3DCommand move) {
                    context.replace(move.entityId(), Velocity3D.TYPE, new Velocity3D(move.direction()));
                } else if (envelope.command() instanceof Aim3DCommand aim) {
                    context.replace(aim.entityId(), Aim3D.TYPE, aim.aim());
                } else if (envelope.command() instanceof Fire3DCommand fire) {
                    context.emit(new ProjectileCreated(EntityId.of("shot"), fire.entityId()),
                            EventAttributes.of(Map.of("origin-z", EventAttributeValue.decimal(fire.origin().z()),
                                    "direction-z", EventAttributeValue.decimal(fire.direction().z()))));
                }
            }
        }
    }
}
