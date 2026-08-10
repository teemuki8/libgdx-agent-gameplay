package io.github.teemuki8.libgdx.agent.gameplay.core.visual;

import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.List;
import java.util.Objects;

/** Bounded immutable visual evidence for one completed gameplay tick. */
public record WorldVisualSnapshot(long tick, List<WorldVisualEntry> entries) {
    /** Validates bounds and sorts entries by semantic entity ID. */
    public WorldVisualSnapshot {
        Objects.requireNonNull(entries, "entries");
        if (tick < 0 || entries.size() > GameplayLimits.VISUAL_ENTRY_MAXIMUM) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.VISUAL_LIMIT_EXCEEDED,
                    "create-visual-snapshot",
                    "non-negative tick and at most "
                            + GameplayLimits.VISUAL_ENTRY_MAXIMUM + " entries",
                    tick + ":" + entries.size(),
                    "Reduce visual evidence volume before capture.");
        }
        entries = entries.stream().map(entry -> Objects.requireNonNull(
                entry, "entry")).sorted().toList();
    }

    /** Returns one required entry by semantic entity ID. */
    public WorldVisualEntry require(EntityId id) {
        return entries.stream().filter(entry -> entry.entityId().equals(id)).findFirst()
                .orElseThrow(() -> GameplayException.validation(
                        GameplayDiagnosticCode.UNKNOWN_ENTITY,
                        "resolve-visual-entry",
                        "captured visual entity",
                        id.value(),
                        "Use an entity ID present in this visual snapshot."));
    }
}
