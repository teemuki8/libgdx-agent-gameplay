package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.IdentifierRules;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Declared animation clips and current deterministic playback state. */
public record Animation(
        Map<String, AnimationClip> clips,
        String currentClip,
        long elapsedTicks,
        int frameIndex) implements Component {
    public static final ComponentType<Animation> TYPE =
            new ComponentType<>("animation", Animation.class);
    private static final int MAX_CLIPS = 64;

    /** Validates and deterministically copies all clips. */
    public Animation {
        Objects.requireNonNull(clips, "clips");
        currentClip = IdentifierRules.requireIdentifier(currentClip, "currentClip");
        if (clips.isEmpty() || clips.size() > MAX_CLIPS || elapsedTicks < 0) {
            throw invalid("1..64 clips and elapsedTicks >= 0",
                    "clips=" + clips.size() + ",elapsedTicks=" + elapsedTicks);
        }
        TreeMap<String, AnimationClip> copy = new TreeMap<>();
        clips.forEach((name, clip) -> copy.put(
                IdentifierRules.requireIdentifier(name, "animation.clip"),
                Objects.requireNonNull(clip, "clip")));
        AnimationClip selected = copy.get(currentClip);
        if (selected == null || frameIndex < 0 || frameIndex >= selected.frames().size()) {
            throw invalid("existing currentClip and frameIndex within its frames",
                    "currentClip=" + currentClip + ",frameIndex=" + frameIndex);
        }
        clips = Collections.unmodifiableMap(copy);
    }

    private static GameplayException invalid(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                "create-animation",
                expected,
                observed,
                "Choose a declared clip and a valid frame index.");
    }
}
