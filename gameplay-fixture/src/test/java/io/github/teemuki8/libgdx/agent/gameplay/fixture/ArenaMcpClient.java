package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Minimal synchronous client for the real newline-delimited harness MCP transport. */
final class ArenaMcpClient implements Closeable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2025-11-25";

    private final BufferedReader input;
    private final BufferedWriter output;
    private long requestId;
    private boolean closed;

    private ArenaMcpClient(ArenaProcess process) {
        input = new BufferedReader(new InputStreamReader(
                process.mcpInput(), StandardCharsets.UTF_8));
        output = new BufferedWriter(new OutputStreamWriter(
                process.mcpOutput(), StandardCharsets.UTF_8));
    }

    static ArenaMcpClient connect(ArenaProcess process) throws Exception {
        ArenaMcpClient client = new ArenaMcpClient(process);
        JsonNode initialized = client.request("initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", "arena-black-box", "version", "1.0")));
        if (!"libgdx-ui-harness".equals(
                initialized.at("/serverInfo/name").asText())) {
            throw new IllegalStateException(
                    "unexpected MCP server identity: " + initialized);
        }
        client.notify("notifications/initialized", Map.of());
        return client;
    }

    List<String> tools() throws Exception {
        JsonNode listed = request("tools/list", Map.of());
        ArrayList<String> names = new ArrayList<>();
        listed.path("tools").forEach(tool -> names.add(tool.path("name").asText()));
        return List.copyOf(names);
    }

    JsonNode tool(String name, Map<String, Object> arguments) throws Exception {
        JsonNode result = request("tools/call", Map.of(
                "name", name, "arguments", arguments));
        if (result.path("isError").asBoolean()) {
            throw new IllegalStateException("MCP tool failed: " + result);
        }
        JsonNode structured = result.path("structuredContent");
        if (!structured.isObject()) {
            throw new IllegalStateException(
                    "MCP tool omitted structured content: " + result);
        }
        return structured;
    }

    JsonNode query(Map<String, Object> locator) throws Exception {
        return tool("ui_query", Map.of(
                "sessionId", ArenaHarness.SESSION_ID,
                "locator", locator,
                "deadlineMillis", 5_000));
    }

    void press(Map<String, Object> locator, int keycode) throws Exception {
        tool("ui_action", Map.of(
                "sessionId", ArenaHarness.SESSION_ID,
                "locator", locator,
                "action", Map.of(
                        "kind", "press", "keycode", keycode, "force", false),
                "deadlineMillis", 5_000));
    }

    JsonNode waitForText(Map<String, Object> locator, String expected) throws Exception {
        JsonNode latest = null;
        for (int attempt = 0; attempt < 360; attempt++) {
            latest = tool("ui_assert", Map.of(
                    "sessionId", ArenaHarness.SESSION_ID,
                    "schemaVersion", 1,
                    "locator", locator,
                    "assertion", Map.of(
                            "kind", "text-equals", "expected", expected),
                    "deadlineMillis", 5_000));
            if ("passed".equals(latest.path("outcome").asText())) {
                return latest;
            }
        }
        throw new IllegalStateException(
                "text did not become " + expected + ": " + latest);
    }

    JsonNode runtimeCompare(Map<String, Object> locator) throws Exception {
        return tool("ui_runtime_compare", Map.of(
                "sessionId", ArenaHarness.SESSION_ID,
                "locator", locator,
                "maxDurationMillis", 5_000,
                "deadlineMillis", 5_000));
    }

    Screenshot screenshot() throws Exception {
        JsonNode result = tool("ui_screenshot", Map.of(
                "sessionId", ArenaHarness.SESSION_ID,
                "maxWidth", 960,
                "maxHeight", 540,
                "maxPixels", 960 * 540,
                "maxPngBytes", 4 * 1_024 * 1_024,
                "deadlineMillis", 5_000));
        JsonNode artifact = result.path("artifact");
        return new Screenshot(
                result.path("width").asInt(), result.path("height").asInt(),
                new Artifact(
                        artifact.path("reference").asText(),
                        artifact.path("mediaType").asText(),
                        artifact.path("byteLength").asLong(),
                        artifact.path("sha256").asText()));
    }

    JsonNode validateLayout() throws Exception {
        return tool("ui_validate_layout", Map.of(
                "sessionId", ArenaHarness.SESSION_ID,
                "spec", Map.of(
                        "targetMode", "stage",
                        "enabledChecks", List.of(
                                "outside-viewport", "clipped-text", "interactive-overlap",
                                "zero-size", "duplicate-test-id",
                                "missing-accessible-name", "obscured"),
                        "minTargetWidth", 64.0,
                        "minTargetHeight", 64.0,
                        "maxAlignmentDelta", 1.0,
                        "minSpacing", 1.0,
                        "failOn", "error",
                        "maxFindings", 128,
                        "maxNodes", 512,
                        "maxDurationMillis", 2_000),
                "deadlineMillis", 5_000));
    }

    private JsonNode request(String method, Map<String, Object> params) throws Exception {
        long id = ++requestId;
        send(Map.of(
                "jsonrpc", "2.0", "id", id, "method", method, "params", params));
        while (true) {
            String line = input.readLine();
            if (line == null) {
                throw new IllegalStateException(
                        "MCP stdout closed while awaiting " + method);
            }
            JsonNode message = JSON.readTree(line);
            if (!message.has("id")) {
                continue;
            }
            if (message.path("id").asLong() != id) {
                throw new IllegalStateException("out-of-order MCP response: " + message);
            }
            if (message.has("error")) {
                throw new IllegalStateException(
                        "MCP request failed: " + message.path("error"));
            }
            return message.path("result");
        }
    }

    private void notify(String method, Map<String, Object> params) throws Exception {
        send(Map.of("jsonrpc", "2.0", "method", method, "params", params));
    }

    private void send(Map<String, Object> message) throws Exception {
        output.write(JSON.writeValueAsString(message));
        output.newLine();
        output.flush();
    }

    @Override public void close() throws java.io.IOException {
        if (closed) {
            return;
        }
        closed = true;
        output.flush();
    }

    record Artifact(String reference, String mediaType, long byteLength, String sha256) {
    }

    record Screenshot(int width, int height, Artifact artifact) {
    }
}
