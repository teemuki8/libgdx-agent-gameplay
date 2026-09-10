package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

/** Bounded snapshot renderer. Camera, batch, model instances and their GL resources remain application-owned. */
public final class GameplayRenderer3D implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final ModelBatch batch;
    private final PerspectiveCamera camera;
    private final Function<EntitySnapshot, ModelInstance> models;
    private final int maxEntries;
    private boolean closed;

    /** Resolver maps logical snapshot identity to a caller-owned model instance, or null to skip it. */
    public GameplayRenderer3D(ModelBatch batch, PerspectiveCamera camera,
            Function<EntitySnapshot, ModelInstance> models, int maxEntries) {
        this.batch = Objects.requireNonNull(batch, "batch");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.models = Objects.requireNonNull(models, "models");
        if (maxEntries < 1 || maxEntries > 4096) throw new IllegalArgumentException("maxEntries: 1..4096");
        this.maxEntries = maxEntries;
    }

    /** Updates the caller's perspective viewport. The application retains camera pose and clipping ownership. */
    public void resize(int width, int height) {
        requireOpen();
        if (width < 1 || height < 1 || width > 16384 || height > 16384) throw new IllegalArgumentException("viewport bounds");
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /** Resolves bounded draw entries before beginning the batch; restores instance transforms after drawing. */
    public void render(WorldSnapshot snapshot) {
        render(snapshot, null);
    }

    /** Draws using optional caller-owned lighting; this adapter never retains or disposes the environment. */
    public void render(WorldSnapshot snapshot, Environment environment) {
        requireOpen();
        Objects.requireNonNull(snapshot, "snapshot");
        var entities = snapshot.entities().stream().filter(entity -> entity.component(Transform3D.TYPE).isPresent())
                .sorted().limit(maxEntries + 1L).toList();
        if (entities.size() > maxEntries) throw new IllegalArgumentException("3D render entry limit exceeded");
        var entries = new ArrayList<Entry>();
        for (EntitySnapshot entity : entities) {
            ModelInstance model = models.apply(entity);
            if (model == null) continue;
            var pose = entity.component(Transform3D.TYPE).orElseThrow();
            var q = pose.rotation();
            var p = pose.position();
            var s = pose.scale();
            for (double value : new double[]{p.x(), p.y(), p.z(), s.x(), s.y(), s.z()}) {
                if (Math.abs(value) > 1e6) throw new IllegalArgumentException("3D render coordinate exceeds 1e6 units");
            }
            Matrix4 transform = new Matrix4(new Vector3((float) p.x(), (float) p.y(), (float) p.z()),
                    new Quaternion((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()),
                    new Vector3((float) s.x(), (float) s.y(), (float) s.z()));
            BoundingBox bounds = model.calculateBoundingBox(new BoundingBox()).mul(transform);
            if (camera.frustum.boundsInFrustum(bounds)) entries.add(new Entry(model, transform));
        }
        boolean depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
        boolean cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
        var integer = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_FUNC, integer);
        int depthFunction = integer.get(0);
        integer.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_CULL_FACE_MODE, integer);
        int cullMode = integer.get(0);
        var mask = BufferUtils.newByteBuffer(1);
        Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, mask);
        boolean depthMask = mask.get(0) != 0;
        batch.begin(camera);
        try {
            for (Entry entry : entries) {
                Matrix4 previous = new Matrix4(entry.model().transform);
                try {
                    entry.model().transform.set(entry.transform());
                    batch.render(entry.model(), environment);
                } finally {
                    entry.model().transform.set(previous);
                }
            }
        } finally {
            try {
                batch.end();
            } finally {
                if (depth) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                if (cull) Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                else Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                Gdx.gl.glDepthFunc(depthFunction);
                Gdx.gl.glCullFace(cullMode);
                Gdx.gl.glDepthMask(depthMask);
            }
        }
    }

    private void requireOpen() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("3D renderer requires render thread");
        if (closed) throw new IllegalStateException("3D renderer closed");
    }
    /** Invalidates only this adapter; the application disposes its own batch/models. */
    @Override public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("3D renderer requires render thread");
        closed = true;
    }
    private record Entry(ModelInstance model, Matrix4 transform) { }
}
