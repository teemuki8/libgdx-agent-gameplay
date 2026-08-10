package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import dev.gdx.uiharness.agentruntime.AgentRuntimeObservationSource;
import dev.gdx.uiharness.core.assertion.AssertionSnapshotSource;
import dev.gdx.uiharness.core.assertion.DeadlineWakeup;
import dev.gdx.uiharness.core.layout.LayoutValidationCheck;
import dev.gdx.uiharness.core.layout.LayoutValidationConfig;
import dev.gdx.uiharness.core.layout.LayoutValidationSeverity;
import dev.gdx.uiharness.core.locator.LocatorEngine;
import dev.gdx.uiharness.core.locator.StrictResolution;
import dev.gdx.uiharness.core.model.SemanticSnapshot;
import dev.gdx.uiharness.core.runtime.RuntimeComparator;
import dev.gdx.uiharness.core.time.Deadline;
import dev.gdx.uiharness.core.time.MonotonicClock;
import dev.gdx.uiharness.core.wait.FrameSignal;
import dev.gdx.uiharness.core.wait.WaitEngine;
import dev.gdx.uiharness.lwjgl3.Lwjgl3FrameFence;
import dev.gdx.uiharness.lwjgl3.Lwjgl3ScreenCapture;
import dev.gdx.uiharness.mcp.ArtifactReference;
import dev.gdx.uiharness.mcp.HarnessMcpServer;
import dev.gdx.uiharness.protocol.ArtifactId;
import dev.gdx.uiharness.protocol.ArtifactMediaType;
import dev.gdx.uiharness.protocol.ArtifactStore;
import dev.gdx.uiharness.protocol.CapabilitySet;
import dev.gdx.uiharness.protocol.FileArtifactStore;
import dev.gdx.uiharness.protocol.HarnessProtocolService;
import dev.gdx.uiharness.scene2d.RenderThreadScheduler;
import dev.gdx.uiharness.scene2d.Scene2dHarness;
import dev.gdx.uiharness.scene2d.Scene2dLayoutValidator;
import dev.gdx.uiharness.scene2d.Scene2dSession;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.UiFrameCorrelation;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.LongSupplier;

/** Render-thread harness session and exclusive stdio MCP lifecycle for the arena. */
public final class ArenaHarness implements AutoCloseable {
    public static final String SESSION_ID = "arena";
    public static final String CORRELATION_TOKEN = "arena-ui-frame";
    private static final int SCHEDULER_CAPACITY = 64;

    private final Scene2dSession session;
    private final RenderThreadScheduler scheduler;
    private final Lwjgl3FrameFence fence;
    private final Scene2dHarness harness;
    private final WaitEngine waits;
    private final ScheduledExecutorService assertionDeadlines;
    private final ExecutorService protocolExecutor;
    private final HarnessProtocolService protocol;
    private final LongSupplier revisions;
    private final LongSupplier frameNumbers;
    private final AgentRuntime runtime;
    private final MonotonicClock clock = MonotonicClock.system();
    private volatile HarnessMcpServer server;
    private Thread mcpThread;

    /** Builds one session on the owning render thread without starting a transport. */
    public ArenaHarness(
            Stage stage,
            InputProcessor input,
            LongSupplier revisions,
            LongSupplier frameNumbers,
            AgentRuntime runtime) {
        this.revisions = revisions;
        this.frameNumbers = frameNumbers;
        this.runtime = runtime;
        session = new Scene2dSession(stage);
        scheduler = new RenderThreadScheduler(SCHEDULER_CAPACITY);
        fence = new Lwjgl3FrameFence();
        harness = new Scene2dHarness(
                stage, input, session, scheduler, fence, revisions, frameNumbers);

        LocatorEngine locators = new StrictResolution();
        assertionDeadlines = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("arena-assertion-deadline").factory());
        AssertionSnapshotSource assertionSnapshots = new AssertionSnapshotSource() {
            @Override public SemanticSnapshot currentSnapshot() {
                return snapshotOnRenderThread();
            }

            @Override public SemanticSnapshot snapshotFor(FrameSignal.Frame completed) {
                if (!scheduler.isOwnerThread()) {
                    throw new IllegalStateException(
                            "completed assertion frames require the render thread");
                }
                return session.snapshot(completed.revision(), completed.frame());
            }
        };
        waits = new WaitEngine(
                this::snapshotOnRenderThread,
                assertionSnapshots,
                locators,
                clock,
                fence,
                DeadlineWakeup.scheduledBy(assertionDeadlines));
        Lwjgl3ScreenCapture capture = new Lwjgl3ScreenCapture(fence, this::snapshotAt);

        CapabilitySet capabilities = new CapabilitySet(List.of(
                "snapshot", "query", "action", "wait", "ui_assert", "screenshot",
                "ui_validate_layout", "ui_runtime_compare"));
        Scene2dLayoutValidator layoutValidator = new Scene2dLayoutValidator(session, locators);
        HarnessProtocolService.LayoutValidationCoordinator layoutCoordinator =
                (spec, deadline) -> scheduler.submit(
                        () -> layoutValidator.validate(
                                revisions.getAsLong(), frameNumbers.getAsLong(),
                                spec.locator() == null ? null : spec.locator().toCore(),
                                layoutConfig(spec), null),
                        deadline);
        RuntimeComparator runtimeComparator = new RuntimeComparator(
                new AgentRuntimeObservationSource(runtime, SESSION_ID));
        HarnessProtocolService.RuntimeCompareCoordinator runtimeCoordinator =
                (locator, deadline) -> scheduler.submit(
                        () -> runtimeComparator.compare(
                                session.snapshot(
                                        revisions.getAsLong(), frameNumbers.getAsLong()),
                                locator.toCore(), new StrictResolution()),
                        deadline);
        HarnessProtocolService.Session protocolSession = new HarnessProtocolService.Session(
                harness, locators, waits, capture, capabilities,
                HarnessProtocolService.TraceController.unsupported(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(layoutCoordinator), Optional.empty(), Optional.empty(),
                Optional.of(runtimeCoordinator));
        protocolExecutor = Executors.newVirtualThreadPerTaskExecutor();
        protocol = new HarnessProtocolService(
                Map.of(SESSION_ID, protocolSession), clock, protocolExecutor);
    }

    /** Returns the one application-owned-stage session. */
    public Scene2dSession session() {
        return session;
    }

    /** Drains all bounded actor work on the render thread. */
    public void drain() {
        scheduler.drain();
    }

    /** Correlates and completes every rendered frame, including unchanged screens. */
    public void publishFrame() {
        var latest = runtime.latestFrame().orElseThrow(
                () -> new IllegalStateException("arena runtime has no completed frame"));
        runtime.uiCorrelations().recordFrame(new UiFrameCorrelation(
                runtime.currentEpoch(), latest.frameId(), SESSION_ID,
                Optional.of(Long.toString(frameNumbers.getAsLong())),
                Optional.of(CORRELATION_TOKEN)));
        fence.completedFrame(revisions.getAsLong(), frameNumbers.getAsLong());
    }

    /** Starts the harness MCP on exclusive process stdio. */
    public void startMcp() {
        startMcp(System.in, System.out);
    }

    Thread startMcp(InputStream input, OutputStream output) {
        if (server != null || mcpThread != null) {
            throw new IllegalStateException("MCP server is already started");
        }
        ArenaArtifactPublisher artifacts = new ArenaArtifactPublisher();
        HarnessMcpServer opened;
        try {
            opened = HarnessMcpServer.open(protocol, artifacts, input, output);
        } catch (RuntimeException failure) {
            artifacts.close();
            throw failure;
        }
        server = opened;
        Thread thread = Thread.ofVirtual().name("arena-mcp").unstarted(() -> {
            try (artifacts; opened) {
                opened.awaitTermination();
            } finally {
                Gdx.app.exit();
            }
        });
        mcpThread = thread;
        try {
            thread.start();
            return thread;
        } catch (RuntimeException failure) {
            mcpThread = null;
            server = null;
            opened.close();
            artifacts.close();
            throw failure;
        }
    }

    /** Returns the bounded local artifact root used only behind opaque receipts. */
    static Path artifactRoot() {
        return Path.of("build", "artifacts");
    }

    @Override public void close() {
        if (server != null) {
            server.close();
        }
        awaitMcpTermination();
        waits.close();
        harness.close();
        fence.close();
        session.close();
        assertionDeadlines.shutdownNow();
        protocolExecutor.shutdownNow();
        scheduler.close();
    }

    private SemanticSnapshot freshSnapshot() {
        return session.snapshot(revisions.getAsLong(), frameNumbers.getAsLong());
    }

    private SemanticSnapshot snapshotAt(long revision, long frame) {
        return session.snapshot(revision, frame);
    }

    private SemanticSnapshot snapshotOnRenderThread() {
        return scheduler.submit(this::freshSnapshot,
                        Deadline.after(clock, Duration.ofSeconds(30)))
                .toCompletableFuture().join();
    }

    private static LayoutValidationConfig layoutConfig(
            dev.gdx.uiharness.protocol.Command.LayoutValidationSpec spec) {
        EnumSet<LayoutValidationCheck> checks = EnumSet.noneOf(LayoutValidationCheck.class);
        for (String check : spec.enabledChecks()) {
            checks.add(LayoutValidationCheck.valueOf(
                    check.toUpperCase(Locale.ROOT).replace('-', '_')));
        }
        return new LayoutValidationConfig(
                checks, spec.minTargetWidth(), spec.minTargetHeight(),
                spec.maxAlignmentDelta(), spec.minSpacing(),
                LayoutValidationSeverity.valueOf(spec.failOn().toUpperCase(Locale.ROOT)),
                spec.maxFindings(), spec.maxNodes());
    }

    private void awaitMcpTermination() {
        Thread thread = mcpThread;
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            if (!thread.join(Duration.ofSeconds(5))) {
                thread.interrupt();
                if (!thread.join(Duration.ofSeconds(1))) {
                    throw new IllegalStateException("arena MCP thread did not terminate");
                }
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while waiting for arena MCP termination", failure);
        }
    }

    private static final class ArenaArtifactPublisher
            implements ArtifactReference.Publisher, AutoCloseable {
        private static final String PREFIX = "artifact:";
        private static final ArtifactStore.Limits LIMITS =
                new ArtifactStore.Limits(64L * 1024 * 1024, 256);
        private static final Duration RETENTION = Duration.ofDays(1);

        private final Clock clock = Clock.systemUTC();
        private final ArtifactStore store = new FileArtifactStore(
                artifactRoot(), LIMITS, clock);

        @Override public ArtifactReference publish(String mediaType, byte[] content) {
            try {
                store.cleanupExpired();
                ArtifactMediaType type = ArtifactMediaType.fromValue(mediaType);
                ArtifactId id = store.put(SESSION_ID, type, content,
                        clock.instant().plus(RETENTION));
                ArtifactStore.Metadata metadata = store.metadata(SESSION_ID, id);
                return new ArtifactReference(
                        PREFIX + id.value(), mediaType, metadata.size(), metadata.sha256());
            } catch (RuntimeException failure) {
                throw new ArtifactReference.ArtifactUnavailableException(
                        "artifact publish unavailable");
            }
        }

        @Override public void close() {
            store.close();
        }
    }
}
