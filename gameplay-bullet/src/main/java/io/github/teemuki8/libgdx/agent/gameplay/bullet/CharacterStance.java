package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.CanonicalComponentWriter;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentCodec;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;

/** Authoritative capsule posture; register TYPE with CODEC in the application registry. */
public record CharacterStance(boolean grounded, boolean crouched) implements Component {
    public static final ComponentType<CharacterStance> TYPE = new ComponentType<>("character-stance", CharacterStance.class);
    /** Canonical field order is grounded, crouched. */
    public static final ComponentCodec<CharacterStance> CODEC = new ComponentCodec<>() {
        @Override public CharacterStance snapshot(CharacterStance value) { return value; }
        @Override public void encode(CharacterStance value, CanonicalComponentWriter writer) {
            writer.bool(value.grounded());
            writer.bool(value.crouched());
        }
    };
}
