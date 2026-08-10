package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Animation;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Render;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Sprite;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Bounds2;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.ScreenBounds;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.VisualEvidenceStatus;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualEntry;
import io.github.teemuki8.libgdx.agent.gameplay.core.visual.WorldVisualSnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntitySnapshot;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Projects completed render-model geometry into immutable top-left framebuffer evidence. */
public final class VisualSnapshotBuilder {
    private final OrthographicCamera camera;
    private final AssetResolver assets;
    private final int framebufferWidth;
    private final int framebufferHeight;
    private final int maxEntries;
    private final double unitConversion;
    private final Thread ownerThread;

    /** Creates a builder using the fixed V1 visual entry maximum. */
    public VisualSnapshotBuilder(
            OrthographicCamera camera,
            AssetResolver assets,
            int framebufferWidth,
            int framebufferHeight) {
        this(camera, assets, framebufferWidth, framebufferHeight,
                GameplayLimits.VISUAL_ENTRY_MAXIMUM, 1.0);
    }

    /** Creates a builder with an explicit render-units-per-physics-unit conversion. */
    public VisualSnapshotBuilder(
            OrthographicCamera camera,
            AssetResolver assets,
            int framebufferWidth,
            int framebufferHeight,
            double unitConversion) {
        this(camera, assets, framebufferWidth, framebufferHeight,
                GameplayLimits.VISUAL_ENTRY_MAXIMUM, unitConversion);
    }

    /** Creates a builder with an application-lowered entry bound. */
    public VisualSnapshotBuilder(
            OrthographicCamera camera,
            AssetResolver assets,
            int framebufferWidth,
            int framebufferHeight,
            int maxEntries) {
        this(camera, assets, framebufferWidth, framebufferHeight, maxEntries, 1.0);
    }

    /** Creates a builder with an application-lowered cap and explicit unit conversion. */
    public VisualSnapshotBuilder(
            OrthographicCamera camera,
            AssetResolver assets,
            int framebufferWidth,
            int framebufferHeight,
            int maxEntries,
            double unitConversion) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.assets = Objects.requireNonNull(assets, "assets");
        if (framebufferWidth < 1 || framebufferHeight < 1
                || maxEntries < 1 || maxEntries > GameplayLimits.VISUAL_ENTRY_MAXIMUM
                || !Double.isFinite(unitConversion) || unitConversion <= 0.0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "configure-visual-snapshot",
                    "positive framebuffer and maxEntries in [1,"
                            + GameplayLimits.VISUAL_ENTRY_MAXIMUM + "]",
                    framebufferWidth + "x" + framebufferHeight + ":" + maxEntries,
                    "Use the current positive framebuffer size and a bounded entry cap.");
        }
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.maxEntries = maxEntries;
        this.unitConversion = unitConversion;
        ownerThread = Thread.currentThread();
    }

    /** Captures every drawable entity, retaining typed unavailable evidence. */
    public WorldVisualSnapshot build(WorldSnapshot snapshot) {
        requireOwner();
        Objects.requireNonNull(snapshot, "snapshot");
        List<EntitySnapshot> entities = GameplayRenderer.orderedEntities(snapshot);
        if (entities.size() > maxEntries) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.VISUAL_LIMIT_EXCEEDED,
                    "capture-visual-snapshot",
                    "at most " + maxEntries + " drawable entities",
                    Integer.toString(entities.size()),
                    "Reduce evidence-bearing entities or use a higher bound within V1.");
        }
        ArrayList<WorldVisualEntry> entries = new ArrayList<>(entities.size());
        for (EntitySnapshot entity : entities) {
            entries.add(entry(snapshot.tick(), entity));
        }
        return new WorldVisualSnapshot(snapshot.tick(), entries);
    }

    private WorldVisualEntry entry(long tick, EntitySnapshot entity) {
        Transform2D transform = entity.component(Transform2D.TYPE).orElseThrow();
        Sprite sprite = entity.component(Sprite.TYPE).orElseThrow();
        Render render = entity.component(Render.TYPE).orElseThrow();
        Animation animation = entity.component(Animation.TYPE).orElse(null);
        Bounds2 worldBounds = worldBounds(transform, sprite);
        Optional<Bounds2> colliderBounds = entity.component(Collider.TYPE)
                .map(collider -> colliderBounds(transform, collider));
        Optional<Vec2> alignmentDelta = colliderBounds.map(bounds -> new Vec2(
                centerX(worldBounds) - centerX(bounds),
                centerY(worldBounds) - centerY(bounds)));
        String region = sprite.region();
        VisualEvidenceStatus status = VisualEvidenceStatus.AVAILABLE;
        try {
            ResolvedFrame resolved = assets.resolve(sprite, animation, tick);
            region = resolved.region();
        } catch (GameplayException failure) {
            if (failure.code() != GameplayDiagnosticCode.MISSING_ASSET) {
                throw failure;
            }
            status = VisualEvidenceStatus.MISSING_ASSET;
            try {
                region = assets.regionFor(sprite, animation);
            } catch (GameplayException ignored) {
                region = sprite.region();
            }
        }

        Optional<ScreenBounds> screenBounds;
        boolean cameraVisible;
        try {
            ScreenBounds projected = project(worldBounds);
            screenBounds = Optional.of(projected);
            cameraVisible = projected.intersects(framebufferWidth, framebufferHeight);
        } catch (GameplayException failure) {
            if (failure.code() != GameplayDiagnosticCode.UNPROJECTABLE_BOUNDS) {
                throw failure;
            }
            screenBounds = Optional.empty();
            cameraVisible = false;
            status = VisualEvidenceStatus.UNPROJECTABLE_BOUNDS;
        }
        return new WorldVisualEntry(
                entity.id(), sprite.asset(), region, transform.position(), worldBounds,
                screenBounds, sprite.origin(), transform.rotationRadians(),
                render.visible(), cameraVisible, render.layer(), render.order(),
                colliderBounds, unitConversion, alignmentDelta, status);
    }

    private ScreenBounds project(Bounds2 bounds) {
        Vector3 first = camera.project(
                new Vector3((float) bounds.minX(), (float) bounds.minY(), 0),
                0, 0, framebufferWidth, framebufferHeight);
        Vector3 second = camera.project(
                new Vector3((float) bounds.maxX(), (float) bounds.minY(), 0),
                0, 0, framebufferWidth, framebufferHeight);
        Vector3 third = camera.project(
                new Vector3((float) bounds.minX(), (float) bounds.maxY(), 0),
                0, 0, framebufferWidth, framebufferHeight);
        Vector3 fourth = camera.project(
                new Vector3((float) bounds.maxX(), (float) bounds.maxY(), 0),
                0, 0, framebufferWidth, framebufferHeight);
        double minX = Math.min(Math.min(first.x, second.x), Math.min(third.x, fourth.x));
        double maxX = Math.max(Math.max(first.x, second.x), Math.max(third.x, fourth.x));
        double minBottomY = Math.min(
                Math.min(first.y, second.y), Math.min(third.y, fourth.y));
        double maxBottomY = Math.max(
                Math.max(first.y, second.y), Math.max(third.y, fourth.y));
        return new ScreenBounds(minX, framebufferHeight - maxBottomY,
                maxX, framebufferHeight - minBottomY);
    }

    private static Bounds2 worldBounds(Transform2D transform, Sprite sprite) {
        double left = -sprite.origin().x() * sprite.visualSize().x();
        double right = left + sprite.visualSize().x();
        double bottom = -sprite.origin().y() * sprite.visualSize().y();
        double top = bottom + sprite.visualSize().y();
        double cosine = Math.cos(transform.rotationRadians());
        double sine = Math.sin(transform.rotationRadians());
        double[] xs = {left, right, left, right};
        double[] ys = {bottom, bottom, top, top};
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < xs.length; index++) {
            double x = transform.position().x() + xs[index] * cosine - ys[index] * sine;
            double y = transform.position().y() + xs[index] * sine + ys[index] * cosine;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new Bounds2(minX, minY, maxX, maxY);
    }

    private static Bounds2 colliderBounds(Transform2D transform, Collider collider) {
        double cosine = Math.cos(transform.rotationRadians());
        double sine = Math.sin(transform.rotationRadians());
        double centerX = transform.position().x()
                + collider.offset().x() * cosine - collider.offset().y() * sine;
        double centerY = transform.position().y()
                + collider.offset().x() * sine + collider.offset().y() * cosine;
        double halfWidth = collider.size().x() * 0.5;
        double halfHeight = collider.size().y() * 0.5;
        double extentX = collider.shape() == Collider.Shape.CIRCLE
                ? halfWidth : halfWidth * Math.abs(cosine) + halfHeight * Math.abs(sine);
        double extentY = collider.shape() == Collider.Shape.CIRCLE
                ? halfHeight : halfWidth * Math.abs(sine) + halfHeight * Math.abs(cosine);
        return new Bounds2(centerX - extentX, centerY - extentY,
                centerX + extentX, centerY + extentY);
    }

    private static double centerX(Bounds2 bounds) {
        return (bounds.minX() + bounds.maxX()) * 0.5;
    }

    private static double centerY(Bounds2 bounds) {
        return (bounds.minY() + bounds.maxY()) * 0.5;
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    "capture-visual-snapshot",
                    "owner thread " + ownerThread.getName(),
                    Thread.currentThread().getName(),
                    "Capture camera and asset evidence on the libGDX render thread.");
        }
    }
}
