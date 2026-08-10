package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaGameState;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaStateComponent;
import io.github.teemuki8.libgdx.agent.gameplay.fixture.ArenaWorldFactory;
import java.util.Objects;

/** Copies fixture domain state into the completed canonical gameplay snapshot. */
public final class ArenaStateSnapshotSystem implements GameSystem {
    private final ArenaGameState state;
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-state-snapshot"), SystemPhase.ANIMATION, 20);

    /** Creates the final authoritative state-copy system. */
    public ArenaStateSnapshotSystem(ArenaGameState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        context.replace(ArenaWorldFactory.STATE_ID, ArenaStateComponent.TYPE, state.snapshot());
    }
}
