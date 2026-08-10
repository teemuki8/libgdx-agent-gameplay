package io.github.teemuki8.libgdx.agent.gameplay.core.component;

/** Canonical V1 component vocabulary. */
public final class StandardComponents {
    private static final ComponentRegistry REGISTRY = ComponentRegistry.builder()
            .register(Transform2D.TYPE)
            .register(Movement.TYPE)
            .register(Health.TYPE)
            .register(Faction.TYPE)
            .register(Lifetime.TYPE)
            .register(Collider.TYPE)
            .register(Sprite.TYPE)
            .register(Animation.TYPE)
            .register(Render.TYPE)
            .build();

    private StandardComponents() {
    }

    /** Returns the immutable standard component registry. */
    public static ComponentRegistry registry() {
        return REGISTRY;
    }
}
