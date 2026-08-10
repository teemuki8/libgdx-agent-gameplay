package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.prefab.PrefabDefinition;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import java.net.URISyntaxException;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArenaPrefabTest {
    private static final Set<String> REGIONS = Set.of(
            "arena-floor", "player-idle", "player-hit", "enemy-idle", "enemy-hit",
            "enemy-death-0", "enemy-death-1", "enemy-death-2", "enemy-death-3",
            "projectile");

    @Test
    void catalogContainsExactlyFourClosedPrefabsWithResolvableAlignedVisuals() {
        var catalog = ArenaWorldFactory.loadPrefabs();
        assertEquals(REGIONS, atlasRegions());
        assertEquals(Set.of("enemy", "player", "projectile", "wall"),
                catalog.definitions().stream().map(prefab -> prefab.id().value())
                        .collect(java.util.stream.Collectors.toSet()));

        for (PrefabDefinition prefab : catalog.definitions()) {
            Sprite sprite = (Sprite) prefab.components().get(Sprite.TYPE);
            Collider collider = (Collider) prefab.components().get(Collider.TYPE);
            if (sprite != null) {
                assertTrue(REGIONS.contains(sprite.region()));
                Animation animation = (Animation) prefab.components().get(Animation.TYPE);
                if (animation != null) {
                    assertTrue(animation.clips().values().stream()
                            .flatMap(clip -> clip.frames().stream()).allMatch(REGIONS::contains));
                }
            }
            if (sprite != null && collider != null) {
                assertTrue(Math.abs(collider.offset().x()) + collider.size().x() * 0.5
                        <= sprite.visualSize().x() * 0.5);
                assertTrue(Math.abs(collider.offset().y()) + collider.size().y() * 0.5
                        <= sprite.visualSize().y() * 0.5);
            }
        }
    }

    private static Set<String> atlasRegions() {
        try {
            var atlasResource = ArenaPrefabTest.class.getResource("/art/arena.atlas");
            var imageResource = ArenaPrefabTest.class.getResource("/art/arena.png");
            if (atlasResource == null || imageResource == null) {
                throw new IllegalStateException("canonical arena atlas resources are missing");
            }
            FileHandle atlas = new FileHandle(new java.io.File(atlasResource.toURI()));
            FileHandle images = new FileHandle(new java.io.File(imageResource.toURI()).getParent());
            TextureAtlas.TextureAtlasData data =
                    new TextureAtlas.TextureAtlasData(atlas, images, false);
            return java.util.stream.StreamSupport.stream(
                            data.getRegions().spliterator(), false)
                    .map(region -> region.name)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (URISyntaxException failure) {
            throw new IllegalStateException("arena atlas resource URI is invalid", failure);
        }
    }
}
