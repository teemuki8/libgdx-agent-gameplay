package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.List;
import java.util.Objects;

/** Ordered logical frames with deterministic tick duration and loop policy. */
public record AnimationClip(List<String> frames, long frameDurationTicks, boolean loop) {
    private static final int MAX_FRAMES = 256;

    /** Validates and copies the frame sequence. */
    public AnimationClip {
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty() || frames.size() > MAX_FRAMES || frameDurationTicks < 1) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-animation-clip",
                    "1..256 frames and frameDurationTicks >= 1",
                    "frames=" + frames.size() + ",duration=" + frameDurationTicks,
                    "Declare a non-empty bounded clip with a positive tick duration.");
        }
        frames = frames.stream()
                .map(frame -> IdentifierRules.requireLogicalAsset(frame, "animation.frame"))
                .toList();
    }
}
