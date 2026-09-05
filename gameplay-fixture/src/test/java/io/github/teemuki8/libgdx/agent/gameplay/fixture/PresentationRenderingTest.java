package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Rgba;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.RenderView;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.AssetResolver;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.GameplayRenderer;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.PresentationFrame;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.RenderViewCoordinates;
import io.github.teemuki8.libgdx.agent.gameplay.libgdx.VisualSnapshotBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PresentationRenderingTest {
    @Test
    void pixelsInputAndEvidenceShareLetterboxedInterpolatedView() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setWindowedMode(200, 100);
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void create() {
                OrthographicCamera camera = new OrthographicCamera(10, 10);
                camera.position.set(5, 5, 0);
                camera.update();
                RenderView view = new RenderView(200, 100, 50, 20, 100, 60);
                Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                pixel.setColor(1, 1, 1, 1);
                pixel.fill();
                Texture texture = new Texture(pixel);
                pixel.dispose();
                TextureAtlas atlas = new TextureAtlas();
                atlas.addRegion("square", new TextureRegion(texture));
                SpriteBatch batch = new SpriteBatch();
                AssetResolver assets = new AssetResolver(atlas);
                GameplayRenderer renderer = new GameplayRenderer(batch, camera, assets);
                try {
                    WorldSnapshot current = new WorldSnapshot(2, List.of(entity(6)));
                    PresentationFrame frame = PresentationFrame.between(
                            new WorldSnapshot(1, List.of(entity(4))), current, 0.5);
                    Gdx.gl.glClearColor(0, 0, 0, 1);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    renderer.render(frame, view);
                    var evidence = new VisualSnapshotBuilder(camera, assets, view, 10, 1.0)
                            .build(frame).require(EntityId.of("square"));
                    assertEquals(95, evidence.screenBounds().orElseThrow().minX(), 0.001);
                    assertEquals(47, evidence.screenBounds().orElseThrow().minY(), 0.001);
                    assertEquals(5.5, evidence.colliderBounds().orElseThrow().minX(), 0.001);
                    assertEquals(-1, evidence.alignmentDelta().orElseThrow().x(), 0.001);
                    Vec2 input = RenderViewCoordinates.worldPosition(camera, view, new Vec2(100, 50))
                            .orElseThrow();
                    assertEquals(5, input.x(), 0.001);
                    assertEquals(5, input.y(), 0.001);
                    assertTrue(RenderViewCoordinates.worldPosition(camera, view, new Vec2(10, 50)).isEmpty());
                    assertTrue(RenderViewCoordinates.worldPosition(camera, view, new Vec2(150, 50)).isEmpty());
                    assertTrue(RenderViewCoordinates.worldPosition(camera, view, new Vec2(100, 80)).isEmpty());
                    Pixmap image = Pixmap.createFromFrameBuffer(0, 0, 200, 100);
                    try {
                        assertEquals(0xffffffff, image.getPixel(100, 50));
                        assertEquals(0x000000ff, image.getPixel(91, 50));
                        com.badlogic.gdx.graphics.PixmapIO.writePNG(
                                Gdx.files.local("build/evidence/presentation-view.png"), image);
                    } finally {
                        image.dispose();
                    }
                    assertEquals(6, current.entities().getFirst().component(Transform2D.TYPE)
                            .orElseThrow().position().x());
                } finally {
                    renderer.close();
                    batch.dispose();
                    atlas.dispose();
                    Gdx.app.exit();
                }
            }
        }, configuration);
    }

    private static EntitySnapshot entity(double x) {
        return new EntitySnapshot(EntityId.of("square"), EntityState.ACTIVE, Map.of(
                Transform2D.TYPE, new Transform2D(new Vec2(x, 5), 0, new Vec2(1, 1), new Vec2(0.5, 0.5)),
                Sprite.TYPE, new Sprite("square", "square", new Vec2(1, 1), new Vec2(0.5, 0.5)),
                Render.TYPE, new Render("world", 0, Rgba.WHITE, true),
                Collider.TYPE, new Collider(Collider.Shape.BOX, new Vec2(1, 1), Vec2.ZERO, false, 1, 1)));
    }
}
