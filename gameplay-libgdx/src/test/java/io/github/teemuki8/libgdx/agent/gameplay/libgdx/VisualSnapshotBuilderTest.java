package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.VisualEvidenceStatus;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VisualSnapshotBuilderTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void projectsTopLeftFramebufferBoundsAndRetainsMissingAssetEvidence() {
        OrthographicCamera camera = new OrthographicCamera(10, 10);
        camera.position.set(5, 5, 0);
        camera.update();
        AssetResolver assets = new AssetResolver(
                new AssetResolverTest.StubAtlas(Map.of()));
        VisualSnapshotBuilder builder = new VisualSnapshotBuilder(camera, assets, 100, 100);
        WorldSnapshot snapshot = new WorldSnapshot(3, List.of(
                entity("player", "player", new Vec2(5, 5)),
                entity("enemy", "missing", new Vec2(8, 8))));

        var visual = builder.build(snapshot);

        var player = visual.require(EntityId.of("player"));
        assertEquals(45.0, player.screenBounds().orElseThrow().minX(), 0.001);
        assertEquals(45.0, player.screenBounds().orElseThrow().minY(), 0.001);
        assertTrue(player.cameraVisible());
        assertEquals(VisualEvidenceStatus.MISSING_ASSET, player.status());
        assertEquals(VisualEvidenceStatus.MISSING_ASSET,
                visual.require(EntityId.of("enemy")).status());
    }

    private static EntitySnapshot entity(String id, String region, Vec2 position) {
        EntityDraft draft = EntityDraft.builder(EntityId.of(id))
                .with(Transform2D.TYPE, new Transform2D(
                        position, 0, new Vec2(1, 1), new Vec2(0.5, 0.5)))
                .with(Sprite.TYPE, new Sprite(
                        region, region, new Vec2(1, 1), new Vec2(0.5, 0.5)))
                .with(Render.TYPE, new Render("actors", 0, Rgba.WHITE, true))
                .build();
        return new EntitySnapshot(draft.id(), EntityState.ACTIVE, draft.components());
    }
}
