package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.AnimationClip;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;

/** Advances declared animation state from fixed simulation ticks. */
public final class AnimationSystem implements GameSystem {
    private final SystemDescriptor descriptor;

    /** Creates the standard animation phase system at the given explicit slot. */
    public AnimationSystem(int slot) {
        descriptor = new SystemDescriptor(
                SystemId.of("animation"), SystemPhase.ANIMATION, slot);
    }

    @Override
    public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void update(SystemContext context) {
        context.query(Animation.TYPE).forEach(entity -> {
            Animation animation = entity.component(Animation.TYPE).orElseThrow();
            AnimationClip clip = animation.clips().get(animation.currentClip());
            long elapsed;
            try {
                elapsed = Math.addExact(animation.elapsedTicks(), 1);
            } catch (ArithmeticException failure) {
                throw GameplayException.validation(
                        GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                        "advance-animation",
                        "elapsedTicks below Long.MAX_VALUE",
                        Long.toString(animation.elapsedTicks()),
                        "Reset or change the animation before its tick counter overflows.");
            }
            long frame = elapsed / clip.frameDurationTicks();
            int frameIndex = clip.loop()
                    ? (int) (frame % clip.frames().size())
                    : (int) Math.min(frame, clip.frames().size() - 1L);
            context.replace(entity.id(), Animation.TYPE, new Animation(
                    animation.clips(), animation.currentClip(), elapsed, frameIndex));
        });
    }
}
