package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Draws completed gameplay snapshots through caller-owned batch, camera, and atlas. */
public final class GameplayRenderer implements AutoCloseable {
    private static final Comparator<EntitySnapshot> DRAW_ORDER = Comparator
            .comparing((EntitySnapshot entity) ->
                    entity.component(Render.TYPE).orElseThrow().layer())
            .thenComparingInt(entity ->
                    entity.component(Render.TYPE).orElseThrow().order())
            .thenComparing(EntitySnapshot::id);

    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final AssetResolver assets;
    private final Thread ownerThread;
    private boolean closed;

    /** Wraps but never disposes the application-owned graphics objects. */
    public GameplayRenderer(
            SpriteBatch batch, OrthographicCamera camera, AssetResolver assets) {
        this.batch = Objects.requireNonNull(batch, "batch");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.assets = Objects.requireNonNull(assets, "assets");
        ownerThread = Thread.currentThread();
    }

    /** Resolves every region first, then draws in layer/order/entity order. */
    public void render(WorldSnapshot snapshot) {
        requireOwner("render-gameplay");
        if (closed) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.RENDERER_CLOSED,
                    "render-gameplay",
                    "open renderer",
                    "closed renderer",
                    "Create a new renderer for later frames.");
        }
        List<DrawEntry> entries = new ArrayList<>();
        for (EntitySnapshot entity : orderedEntities(snapshot)) {
            Render render = entity.component(Render.TYPE).orElseThrow();
            if (!render.visible()) {
                continue;
            }
            Sprite sprite = entity.component(Sprite.TYPE).orElseThrow();
            Transform2D transform = entity.component(Transform2D.TYPE).orElseThrow();
            Animation animation = entity.component(Animation.TYPE).orElse(null);
            entries.add(new DrawEntry(transform, sprite, render,
                    assets.resolve(sprite, animation, snapshot.tick())));
        }

        Color previousColor = new Color(batch.getColor());
        Matrix4 previousProjection = new Matrix4(batch.getProjectionMatrix());
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        try {
            for (DrawEntry entry : entries) {
                draw(entry);
            }
        } finally {
            batch.end();
            batch.setColor(previousColor);
            batch.setProjectionMatrix(previousProjection);
        }
    }

    static List<EntitySnapshot> orderedEntities(WorldSnapshot snapshot) {
        return snapshot.entities().stream()
                .filter(entity -> entity.component(Transform2D.TYPE).isPresent())
                .filter(entity -> entity.component(Sprite.TYPE).isPresent())
                .filter(entity -> entity.component(Render.TYPE).isPresent())
                .sorted(DRAW_ORDER)
                .toList();
    }

    private void draw(DrawEntry entry) {
        Transform2D transform = entry.transform();
        Sprite sprite = entry.sprite();
        float width = (float) sprite.visualSize().x();
        float height = (float) sprite.visualSize().y();
        float originX = (float) (sprite.origin().x() * sprite.visualSize().x());
        float originY = (float) (sprite.origin().y() * sprite.visualSize().y());
        float x = (float) transform.position().x() - originX;
        float y = (float) transform.position().y() - originY;
        batch.setColor(
                (float) entry.render().tint().red(),
                (float) entry.render().tint().green(),
                (float) entry.render().tint().blue(),
                (float) entry.render().tint().alpha());
        batch.draw(entry.frame().textureRegion(), x, y, originX, originY,
                width, height, 1, 1,
                (float) Math.toDegrees(transform.rotationRadians()));
    }

    /** Clears adapter state without touching caller-owned graphics resources. */
    @Override
    public void close() {
        requireOwner("close-gameplay-renderer");
        closed = true;
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    operation,
                    "owner thread " + ownerThread.getName(),
                    Thread.currentThread().getName(),
                    "Render and close on the libGDX render thread.");
        }
    }

    private record DrawEntry(
            Transform2D transform, Sprite sprite, Render render, ResolvedFrame frame) {
    }
}
