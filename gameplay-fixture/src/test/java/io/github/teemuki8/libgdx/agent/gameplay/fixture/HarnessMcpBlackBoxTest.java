package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class HarnessMcpBlackBoxTest {
    private static final List<String> TOOL_CATALOG = List.of(
            "ui_sessions", "ui_snapshot", "ui_query", "ui_action",
            "ui_assert", "ui_wait", "ui_screenshot",
            "ui_inspect_compare", "ui_typography_diagnose", "ui_layout_diagnose",
            "ui_trace_start", "ui_trace_stop", "ui_scenarios", "ui_scenario_start",
            "ui_navigation_inspect", "ui_navigation_validate", "ui_validate_layout",
            "ui_matrix_run", "ui_matrix_results", "ui_runtime_compare", "ui_trace_query",
            "ui_semantic_compare", "ui_capabilities");
    private static final Map<String, Object> START = testId("start-button");
    private static final Map<String, Object> RESET = testId("reset-button");
    private static final Map<String, Object> SCREEN = testId("screen-value");
    private static final Map<String, Object> HEALTH = testId("health-value");
    private static final Map<String, Object> ENEMY_HEALTH = testId("enemy-health-value");
    private static final Map<String, Object> SCORE = testId("score-value");

    @Test
    void realProcessCompletesTheProductionArenaLoopThroughStdioMcp() throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize().getParent();
        assertNoArtifacts(root.resolve(ArenaHarness.artifactRoot()));
        try (ArenaProcess process = ArenaProcess.start();
                ArenaMcpClient client = ArenaMcpClient.connect(process)) {
            assertEquals(TOOL_CATALOG, client.tools());
            JsonNode sessions = client.tool("ui_sessions", Map.of());
            assertTrue(sessions.toString().contains(ArenaHarness.SESSION_ID),
                    sessions.toPrettyString());
            JsonNode start = client.query(START);
            assertEquals(1, start.path("matchCount").asInt());
            assertEquals("Start game",
                    start.path("matches").path(0).path("accessibleName").asText());
            assertComparison(client, SCREEN, "TITLE");

            client.press(START, Input.Keys.ENTER);
            client.waitForText(SCREEN, "PLAYING");
            client.waitForText(HEALTH, "3");
            client.waitForText(ENEMY_HEALTH, "3");
            client.waitForText(SCORE, "0");
            assertComparison(client, SCREEN, "PLAYING");
            assertComparison(client, HEALTH, "3");
            assertComparison(client, SCORE, "0");
            retain(root, client.screenshot(), "01-actionable.png");

            client.press(RESET, Input.Keys.W);
            client.press(RESET, Input.Keys.D);
            client.press(RESET, Input.Keys.SPACE);
            retain(root, client.screenshot(), "02-movement-fire.png");
            client.waitForText(ENEMY_HEALTH, "2");

            client.press(RESET, Input.Keys.SPACE);
            client.waitForText(ENEMY_HEALTH, "1");
            client.press(RESET, Input.Keys.SPACE);
            client.waitForText(SCORE, "300");
            client.waitForText(ENEMY_HEALTH, "0");
            assertComparison(client, SCORE, "300");
            retain(root, client.screenshot(), "03-enemy-death.png");

            JsonNode layout = client.validateLayout();
            retainJson(root, layout, "layout-validation.json");
            assertEquals("PASS", layout.path("result").path("status").asText(),
                    layout.toPrettyString());
            assertEquals(7, layout.path("result").path("appliedConfig")
                    .path("enabledChecks").size(), layout.toPrettyString());
            for (JsonNode finding : layout.path("result").path("findings")) {
                assertFalse("ERROR".equals(finding.path("severity").asText()),
                        layout.toPrettyString());
                assertFalse("CHECK_UNAVAILABLE".equals(finding.path("reason").asText()),
                        layout.toPrettyString());
            }

            client.press(RESET, Input.Keys.ENTER);
            client.waitForText(SCREEN, "PLAYING");
            client.waitForText(HEALTH, "3");
            client.waitForText(ENEMY_HEALTH, "3");
            client.waitForText(SCORE, "0");
            assertComparison(client, SCREEN, "PLAYING");
            assertComparison(client, HEALTH, "3");
            assertComparison(client, SCORE, "0");
            retain(root, client.screenshot(), "04-reset.png");
        }
        assertNoArtifacts(root.resolve(ArenaHarness.artifactRoot()));
    }

    private static void assertComparison(
            ArenaMcpClient client,
            Map<String, Object> locator,
            String expected) throws Exception {
        JsonNode comparison = client.runtimeCompare(locator);
        assertEquals("EQUAL", comparison.path("status").asText(),
                comparison.toPrettyString());
        assertEquals(expected, comparison.path("displayedValue").asText());
        assertEquals(expected, comparison.path("runtimeValue").asText());
        assertEquals(comparison.path("displayedFrame").asLong(),
                comparison.path("runtimeFrame").asLong());
    }

    private static void retain(
            Path root, ArenaMcpClient.Screenshot screenshot, String filename) throws Exception {
        assertEquals(960, screenshot.width());
        assertEquals(540, screenshot.height());
        ArenaMcpClient.Artifact artifact = screenshot.artifact();
        assertEquals("image/png", artifact.mediaType());
        assertTrue(artifact.reference().startsWith("artifact:"));
        assertFalse(artifact.reference().contains("/"));
        assertFalse(artifact.reference().contains("\\"));
        Path stored = findArtifact(root.resolve(ArenaHarness.artifactRoot()), artifact);
        try (InputStream input = Files.newInputStream(stored)) {
            BufferedImage image = ImageIO.read(input);
            assertEquals(960, image.getWidth());
            assertEquals(540, image.getHeight());
        }
        Path destination = root.resolve("docs/evidence/screenshots").resolve(filename);
        Files.createDirectories(destination.getParent());
        if (Files.notExists(destination)) {
            Files.copy(stored, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void retainJson(Path root, JsonNode value, String filename) throws Exception {
        Path destination = root.resolve("docs/evidence").resolve(filename);
        Files.createDirectories(destination.getParent());
        if (Files.notExists(destination)) {
            Files.writeString(destination, value.toPrettyString() + System.lineSeparator());
        }
    }

    private static Path findArtifact(
            Path artifactRoot, ArenaMcpClient.Artifact artifact) throws Exception {
        try (var files = Files.walk(artifactRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> size(path) == artifact.byteLength())
                    .filter(path -> sha256(path).equals(artifact.sha256()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "opaque screenshot receipt did not match local bounded store"));
        }
    }

    private static void assertNoArtifacts(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count(),
                    "session artifacts were not removed");
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to inspect artifact size", failure);
        }
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("failed to hash artifact", failure);
        }
    }

    private static Map<String, Object> testId(String value) {
        return Map.of("kind", "test-id", "testId", value);
    }
}
