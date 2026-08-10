package io.github.teemuki8.libgdx.agent.gameplay.core.component;

/** Explicit immutable-snapshot and canonical-field-order contract for one component type. */
public interface ComponentCodec<T extends Component> {
    /** Returns a deeply immutable detached value for a completed world snapshot. */
    T snapshot(T component);

    /** Writes every authoritative field in a stable documented order. */
    void encode(T component, CanonicalComponentWriter writer);
}
