package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.command.AimCommand;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import java.util.Objects;

/** Applies authored aim commands to independently owned arena state. */
public final class ArenaInputSystem implements GameSystem {
    private final ArenaGameState state;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-input"), SystemPhase.INPUT, 20);

    /** Creates the production command consumer. */
    public ArenaInputSystem(ArenaGameState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        context.commands().stream().map(command -> command.command())
                .filter(AimCommand.class::isInstance)
                .map(AimCommand.class::cast)
                .filter(command -> command.entityId().equals(
                        io.github.teemuki8.libgdx.agent.gameplay.fixture
                                .ArenaWorldFactory.PLAYER_ID))
                .forEach(command -> state.aim(command.direction()));
    }
}
