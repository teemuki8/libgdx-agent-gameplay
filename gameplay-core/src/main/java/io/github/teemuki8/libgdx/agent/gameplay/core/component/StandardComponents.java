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

    static ComponentCodec<?> codec(ComponentType<?> type) {
        if (!java.util.Set.of(
                Transform2D.TYPE, Movement.TYPE, Health.TYPE, Faction.TYPE,
                Lifetime.TYPE, Collider.TYPE, Sprite.TYPE, Animation.TYPE,
                Render.TYPE).contains(type)) {
            throw io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException
                    .validation(
                            io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic
                                    .GameplayDiagnosticCode.UNKNOWN_COMPONENT_TYPE,
                            "register-component-codec",
                            "standard component or explicit custom codec",
                            type.id(),
                            "Call register(type, codec) for every custom component.");
        }
        return new ComponentCodec<Component>() {
            @Override
            public Component snapshot(Component component) {
                return component;
            }

            @Override
            public void encode(Component component, CanonicalComponentWriter writer) {
                io.github.teemuki8.libgdx.agent.gameplay.core.replay.CanonicalWorldEncoder
                        .encodeStandardComponent(writer, component);
            }
        };
    }
}
