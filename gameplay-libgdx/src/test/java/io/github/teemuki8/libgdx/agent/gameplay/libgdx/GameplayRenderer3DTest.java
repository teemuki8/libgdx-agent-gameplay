package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityState;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameplayRenderer3DTest {
    @Test void realDepthKeepsNearRedBoxInFrontOfLaterBlueBox() {
        var failure = new AtomicReference<Throwable>();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(96, 96);
        config.disableAudio(true);
        org.lwjgl.glfw.GLFW.glfwInitHint(org.lwjgl.glfw.GLFW.GLFW_PLATFORM, org.lwjgl.glfw.GLFW.GLFW_PLATFORM_X11);
        new RendererTestApplication(new ApplicationAdapter() {
            @Override public void create() {
                try {
                    var builder = new ModelBuilder();
                    var red = builder.createBox(1, 1, 1, new Material(ColorAttribute.createDiffuse(Color.RED)),
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    var blue = builder.createBox(1, 1, 1, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    var batch = new ModelBatch();
                    try (
                            var renderer = new GameplayRenderer3D(batch, new PerspectiveCamera(67, 96, 96),
                                    entity -> new ModelInstance(entity.id().value().equals("a") ? red : blue), 8)) {
                        renderer.resize(96, 96);
                        Gdx.gl.glClearColor(0, 0, 0, 1);
                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
                        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                        assertThrows(IllegalArgumentException.class, () -> renderer.render(
                                new WorldSnapshot(1, List.of(entity("huge", Double.MAX_VALUE)))));
                        var environment = new Environment();
                        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1, 1, 1, 1));
                        renderer.render(new WorldSnapshot(1, List.of(entity("a", -2), entity("b", -4))), environment);
                        assertTrue(Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST));
                        assertTrue(Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE));
                        var image = Pixmap.createFromFrameBuffer(48, 48, 1, 1);
                        int pixel = image.getPixel(0, 0);
                        image.dispose();
                        assertTrue((pixel >>> 24) > 150 && ((pixel >>> 8) & 255) < 50);
                    } finally {
                        batch.dispose();
                        red.dispose();
                        blue.dispose();
                    }
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private static EntitySnapshot entity(String id, double z) {
        return new EntitySnapshot(EntityId.of(id), EntityState.ACTIVE, Map.of(Transform3D.TYPE,
                new Transform3D(new Vec3(0, 0, z), new QuaternionValue(0, 0, 0, 1))));
    }
}
