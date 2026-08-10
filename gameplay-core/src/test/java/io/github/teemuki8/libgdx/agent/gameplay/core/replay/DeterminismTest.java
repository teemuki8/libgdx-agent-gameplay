package io.github.teemuki8.libgdx.agent.gameplay.core.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.AimCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.command.CommandEnvelope;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.random.DeterministicRandom;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DeterminismTest {
    private static final EntityId PLAYER = EntityId.of("player");
    private static final CommandSourceId INPUT = CommandSourceId.of("input");

    @Test
    void identicalSeedAndTranscriptProduceIdenticalDigestAtEveryTick() {
        CommandTranscript transcript = transcript(new Vec2(1, 0));

        TranscriptResult first = runner(42).run(transcript, 40);
        TranscriptResult second = runner(42).run(transcript, 40);

        assertEquals(first.tickDigests(), second.tickDigests());
        assertEquals(first.eventDigests(), second.eventDigests());
    }

    @Test
    void oneChangedCommandChangesTheFirstAffectedDigest() {
        TranscriptResult left = runner(42).run(transcript(new Vec2(1, 0)), 40);
        TranscriptResult right = runner(42).run(transcript(new Vec2(0, 1)), 40);

        assertEquals(left.tickDigests().subList(0, 31),
                right.tickDigests().subList(0, 31));
        assertNotEquals(left.tickDigests().get(31), right.tickDigests().get(31));
    }

    @Test
    void transcriptSortsCommandsAndSplitMix64HasOwnedStableVectors() {
        CommandEnvelope later = command(4, 2, new Vec2(1, 0));
        CommandEnvelope earlier = command(2, 1, new Vec2(0, 1));

        assertEquals(List.of(earlier, later),
                new CommandTranscript(List.of(later, earlier)).commands());
        DeterministicRandom random = new DeterministicRandom(0);
        assertEquals("e220a8397b1dcdaf", Long.toUnsignedString(random.nextLong(), 16));
        assertEquals("6e789e6aa1b965f4", Long.toUnsignedString(random.nextLong(), 16));
    }

    private static TranscriptRunner runner(long seed) {
        return new TranscriptRunner(() -> world(seed));
    }

    private static GameWorld world(long seed) {
        DeterministicRandom random = new DeterministicRandom(seed);
        Transform2D initial = new Transform2D(
                new Vec2(random.nextDouble(), random.nextDouble()),
                0, new Vec2(1, 1), new Vec2(0.5, 0.5));
        return GameWorld.builder(GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(EntityDraft.builder(PLAYER)
                        .with(Transform2D.TYPE, initial)
                        .build()))
                .system(new AimSystem())
                .build();
    }

    private static CommandTranscript transcript(Vec2 direction) {
        return new CommandTranscript(List.of(command(31, 1, direction)));
    }

    private static CommandEnvelope command(long tick, long sequence, Vec2 direction) {
        return new CommandEnvelope(tick, INPUT, sequence,
                new AimCommand(PLAYER, direction));
    }

    private static final class AimSystem implements GameSystem {
        @Override
        public SystemDescriptor descriptor() {
            return new SystemDescriptor(
                    SystemId.of("apply-aim"), SystemPhase.INPUT, 10);
        }

        @Override
        public void update(SystemContext context) {
            for (CommandEnvelope envelope : context.commands()) {
                if (envelope.command() instanceof AimCommand aim) {
                    Transform2D before = context.query(Transform2D.TYPE).getFirst()
                            .component(Transform2D.TYPE).orElseThrow();
                    context.replace(aim.entityId(), Transform2D.TYPE,
                            new Transform2D(before.position(),
                                    Math.atan2(aim.direction().y(), aim.direction().x()),
                                    before.size(), before.pivot()));
                }
            }
        }
    }
}
