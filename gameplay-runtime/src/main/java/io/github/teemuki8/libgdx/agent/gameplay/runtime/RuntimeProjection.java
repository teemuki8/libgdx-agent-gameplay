package io.github.teemuki8.libgdx.agent.gameplay.runtime;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Component;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.ComponentType;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import java.util.Map;

/** Explicit non-reflective projection of one gameplay component into runtime values. */
public interface RuntimeProjection<T extends Component> {
    /** Returns the exact component type accepted by this projection. */
    ComponentType<T> componentType();

    /** Returns bounded, stable property names and immutable runtime values. */
    Map<String, RuntimeValue> project(EntitySnapshot entity, T component);
}
