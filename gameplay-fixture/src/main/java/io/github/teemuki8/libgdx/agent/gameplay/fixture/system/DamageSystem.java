package io.github.teemuki8.libgdx.agent.gameplay.fixture.system;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Faction;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Health;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.DamageApplied;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributeValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.EventAttributes;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import java.util.HashMap;
import java.util.Map;

/** Converts stable Box2D contact endpoints into attributed damage and projectile removal. */
public final class DamageSystem implements GameSystem {
    private final SystemDescriptor descriptor = new SystemDescriptor(
            SystemId.of("arena-damage"), SystemPhase.GAMEPLAY, 20);

    @Override public SystemDescriptor descriptor() {
        return descriptor;
    }

    @Override public void update(SystemContext context) {
        Map<EntityId, EntityView> entities = new HashMap<>();
        context.query(Faction.TYPE).forEach(entity -> entities.put(entity.id(), entity));
        Map<EntityId, Health> health = new HashMap<>();
        context.query(Health.TYPE).forEach(entity -> health.put(
                entity.id(), entity.component(Health.TYPE).orElseThrow()));
        for (var envelope : context.events()) {
            if (!(envelope.event() instanceof CollisionStarted collision)) {
                continue;
            }
            EntityView first = entities.get(collision.first());
            EntityView second = entities.get(collision.second());
            if (first == null || second == null) {
                continue;
            }
            String firstFaction = first.component(Faction.TYPE).orElseThrow().value();
            String secondFaction = second.component(Faction.TYPE).orElseThrow().value();
            if ("projectile".equals(firstFaction) || "projectile".equals(secondFaction)) {
                EntityId projectile = "projectile".equals(firstFaction)
                        ? first.id() : second.id();
                EntityView target = projectile.equals(first.id()) ? second : first;
                if ("enemy".equals(target.component(Faction.TYPE).orElseThrow().value())) {
                    damage(context, entities, health, target.id(), projectile, collision);
                }
                context.despawn(projectile);
            } else if (("player".equals(firstFaction) && "enemy".equals(secondFaction))
                    || ("enemy".equals(firstFaction) && "player".equals(secondFaction))) {
                EntityId player = "player".equals(firstFaction) ? first.id() : second.id();
                EntityId enemy = player.equals(first.id()) ? second.id() : first.id();
                damage(context, entities, health, player, enemy, collision);
            }
        }
    }

    private static void damage(
            SystemContext context,
            Map<EntityId, EntityView> entities,
            Map<EntityId, Health> health,
            EntityId subject,
            EntityId source,
            CollisionStarted collision) {
        Health previous = health.get(subject);
        if (previous == null || previous.current() == 0) {
            return;
        }
        Health updated = new Health(previous.current() - 1, previous.max());
        health.put(subject, updated);
        context.replace(subject, Health.TYPE, updated);
        EntityView entity = entities.get(subject);
        if (entity != null && updated.current() > 0) {
            entity.component(Animation.TYPE).ifPresent(animation ->
                    context.replace(subject, Animation.TYPE,
                            new Animation(animation.clips(), "hit", 0, 0)));
        }
        String contactId = collision.firstFixtureId() + "|" + collision.secondFixtureId();
        context.emit(new DamageApplied(subject, source, 1), EventAttributes.of(Map.of(
                "contact", EventAttributeValue.string(contactId))));
    }
}
