package io.github.teemuki8.libgdx.agent.gameplay.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.CommandSourceId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommandBufferTest {
    @Test
    void ordersBySourceAndSequenceRatherThanRegistrationOrder() {
        CommandBuffer buffer = new CommandBuffer(GameplayLimits.defaults());
        buffer.enqueue(envelope(7, "player-b", 0,
                new FireCommand(EntityId.of("player"), Vec2.ZERO, new Vec2(1, 0))));
        buffer.enqueue(envelope(7, "player-a", 1,
                new MoveCommand(EntityId.of("player"), new Vec2(0, 1))));
        buffer.enqueue(envelope(7, "player-a", 0,
                new MoveCommand(EntityId.of("player"), new Vec2(1, 0))));

        assertEquals(List.of("player-a:0", "player-a:1", "player-b:0"),
                buffer.commandsFor(7).stream()
                        .map(value -> value.source().value() + ":" + value.sequence())
                        .toList());
        assertThrows(UnsupportedOperationException.class,
                () -> buffer.commandsFor(7).clear());
    }

    @Test
    void rejectsDuplicateLateAndDistantCommandsWithDistinctCodes() {
        CommandBuffer duplicate = new CommandBuffer(GameplayLimits.defaults());
        CommandEnvelope command = envelope(2, "player", 0,
                new MoveCommand(EntityId.of("player"), Vec2.ZERO));
        duplicate.enqueue(command);
        assertCode(GameplayDiagnosticCode.DUPLICATE_COMMAND_SEQUENCE,
                () -> duplicate.enqueue(command));

        duplicate.advanceTo(3);
        assertCode(GameplayDiagnosticCode.LATE_COMMAND,
                () -> duplicate.enqueue(envelope(2, "player", 1,
                        new MoveCommand(EntityId.of("player"), Vec2.ZERO))));
        assertCode(GameplayDiagnosticCode.COMMAND_WINDOW_EXCEEDED,
                () -> duplicate.enqueue(envelope(4_100, "player", 2,
                        new MoveCommand(EntityId.of("player"), Vec2.ZERO))));
    }

    @Test
    void enforcesTheConfiguredQueuedCommandLimit() {
        CommandBuffer buffer = new CommandBuffer(limits(2, 4_096));
        buffer.enqueue(envelope(1, "keyboard", 0,
                new MoveCommand(EntityId.of("player"), Vec2.ZERO)));
        buffer.enqueue(envelope(1, "keyboard", 1,
                new FireCommand(EntityId.of("player"), Vec2.ZERO, new Vec2(1, 0))));

        assertCode(GameplayDiagnosticCode.COMMAND_LIMIT_EXCEEDED,
                () -> buffer.enqueue(envelope(1, "keyboard", 2,
                        new AimCommand(EntityId.of("player"), new Vec2(1, 0)))));
    }

    @Test
    void rejectsSourceSequenceThatWouldRunBackwardAcrossTicks() {
        CommandBuffer buffer = new CommandBuffer(GameplayLimits.defaults());
        buffer.enqueue(envelope(4, "keyboard", 1,
                new MoveCommand(EntityId.of("player"), Vec2.ZERO)));
        buffer.enqueue(envelope(3, "keyboard", 0,
                new MoveCommand(EntityId.of("player"), Vec2.ZERO)));

        assertCode(GameplayDiagnosticCode.NON_MONOTONIC_COMMAND_SEQUENCE,
                () -> buffer.enqueue(envelope(3, "keyboard", 2,
                        new FireCommand(EntityId.of("player"), Vec2.ZERO, new Vec2(1, 0)))));
    }

    private static CommandEnvelope envelope(
            long tick, String source, long sequence, GameplayCommand command) {
        return new CommandEnvelope(tick, CommandSourceId.of(source), sequence, command);
    }

    private static GameplayLimits limits(int commands, int events) {
        return new GameplayLimits(10_000, 64, 256, commands, 4_096, events,
                10_000, 4 * 1024 * 1024);
    }

    private static void assertCode(GameplayDiagnosticCode code, Runnable operation) {
        GameplayException failure = assertThrows(GameplayException.class, operation::run);
        assertEquals(code, failure.code());
    }
}
