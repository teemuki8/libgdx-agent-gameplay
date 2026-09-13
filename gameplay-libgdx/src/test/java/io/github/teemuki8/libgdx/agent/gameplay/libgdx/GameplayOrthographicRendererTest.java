package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
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
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayOrthographicRendererTest {
    @Test void orthographicWorldScaleAndDepthSurviveWindowResize() {
        var failure = new AtomicReference<Throwable>();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(192, 96);
        config.disableAudio(true);
        org.lwjgl.glfw.GLFW.glfwInitHint(org.lwjgl.glfw.GLFW.GLFW_PLATFORM,
                org.lwjgl.glfw.GLFW.GLFW_PLATFORM_X11);
        new RendererTestApplication(new ApplicationAdapter() {
            @Override public void create() {
                try {
                    var builder = new ModelBuilder();
                    var red = builder.createBox(1, 1, 1,
                            new Material(ColorAttribute.createDiffuse(Color.RED)),
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    var blue = builder.createBox(1, 1, 1,
                            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    var batch = new ModelBatch();
                    try {
                        var camera = new OrthographicCamera(4, 4);
                        camera.near = .1f;
                        camera.far = 30;
                        camera.zoom = 1.25f;
                        var constructor = assertDoesNotThrow(() -> GameplayRenderer3D.class.getConstructor(
                                ModelBatch.class, Camera.class, Function.class, int.class),
                                "Snapshot rendering must support an application-owned orthographic camera");
                        Function<EntitySnapshot, ModelInstance> models = entity ->
                                new ModelInstance(entity.id().value().equals("a-near") ? red : blue);
                        try (var renderer = constructor.newInstance(batch, camera, models, 8)) {
                            renderer.resize(192, 96);
                            assertEquals(8, camera.viewportWidth);
                            assertEquals(4, camera.viewportHeight);
                            assertEquals(1.25f, camera.zoom);
                            Gdx.gl.glViewport(0, 0, 192, 96);
                            Gdx.gl.glClearColor(0, 0, 0, 1);
                            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
                            var environment = new Environment();
                            environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1, 1, 1, 1));
                            renderer.render(new WorldSnapshot(1,
                                    List.of(entity("a-near", -2), entity("z-far", -5))), environment);
                            var image = Pixmap.createFromFrameBuffer(0, 0, 192, 96);
                            try {
                                int centre = image.getPixel(96, 48);
                                assertTrue((centre >>> 24) > 150 && ((centre >>> 8) & 255) < 50,
                                        "The nearer red cube must occlude the far blue cube");
                                assertEquals(0x000000ff, image.getPixel(120, 48),
                                        "A one-metre cube must retain its authored screen scale");
                            } finally {
                                image.dispose();
                            }
                            renderer.resize(96, 96);
                            assertEquals(4, camera.viewportWidth);
                            assertEquals(4, camera.viewportHeight);
                            assertEquals(1.25f, camera.zoom);
                            Gdx.gl.glViewport(0, 0, 96, 96);
                            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
                            renderer.render(new WorldSnapshot(2,
                                    List.of(entity("a-near", -2), entity("z-far", -5))), environment);
                            var resized = Pixmap.createFromFrameBuffer(0, 0, 96, 96);
                            try {
                                assertTrue((resized.getPixel(48, 48) >>> 24) > 150);
                                assertTrue((resized.getPixel(55, 48) >>> 24) > 150,
                                        "Resize must preserve the cube's pixel scale for equal surface height");
                                assertEquals(0x000000ff, resized.getPixel(60, 48));
                            } finally {
                                resized.dispose();
                            }
                            for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY,
                                    Float.MAX_VALUE, Float.MIN_VALUE}) {
                                camera.viewportHeight = invalid;
                                assertThrows(IllegalArgumentException.class, () -> renderer.resize(96, 96),
                                        "Unrepresentable effective span must fail: " + invalid);
                                assertEquals(4, camera.viewportWidth, "Invalid span must not partly resize");
                                assertEquals(invalid, camera.viewportHeight);
                            }
                        }
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
                new Transform3D(new Vec3(0, 0, z), QuaternionValue.IDENTITY)));
    }
}
