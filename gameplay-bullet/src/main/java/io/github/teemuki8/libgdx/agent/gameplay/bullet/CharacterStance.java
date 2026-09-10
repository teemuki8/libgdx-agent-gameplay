package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.CanonicalComponentWriter;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentCodec;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;

/** Authoritative capsule posture; register TYPE with CODEC in the application registry. */
public record CharacterStance(boolean grounded, double crouchFraction) implements Component {
    public CharacterStance { CharacterConfig.requireFraction(crouchFraction); }
    /** Compatibility constructor maps the old binary posture to exact endpoints. */
    public CharacterStance(boolean grounded, boolean crouched) { this(grounded, crouched ? 1.0 : 0.0); }
    /** Any partial or complete crouch, including blocked standing, is crouched. */
    public boolean crouched() { return crouchFraction > 0; }
    public static final ComponentType<CharacterStance> TYPE = new ComponentType<>("character-stance", CharacterStance.class);
    /** Canonical field order is grounded, derived crouched, crouchFraction. */
    public static final ComponentCodec<CharacterStance> CODEC = new ComponentCodec<>() {
        @Override public CharacterStance snapshot(CharacterStance value) { return value; }
        @Override public void encode(CharacterStance value, CanonicalComponentWriter writer) {
            writer.bool(value.grounded());
            writer.bool(value.crouched());
            writer.decimal(value.crouchFraction());
        }
    };
}
