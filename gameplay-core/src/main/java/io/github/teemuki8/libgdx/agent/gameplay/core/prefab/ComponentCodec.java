package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import java.util.Set;

/** Explicit non-reflective decoder for one registered component type. */
public interface ComponentCodec<T extends Component> {
    /** Returns the stable component type. */
    ComponentType<T> type();

    /** Returns every accepted field except the common {@code type} field. */
    Set<String> acceptedFields();

    /** Decodes one validated field set. */
    T decode(ComponentFields fields);
}
