package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Real fat-JAR arena child process with inherited Xvfb display and bounded shutdown. */
final class ArenaProcess implements AutoCloseable {
    private final Process process;

    private ArenaProcess(Process process) {
        this.process = process;
    }

    static ArenaProcess start() throws IOException {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path root = module.getParent();
        Path jar = module.resolve(
                "build/libs/libgdx-agent-gameplay-fixture.jar");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-jar", jar.toString(), "--mcp")
                .directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        return new ArenaProcess(process);
    }

    InputStream mcpInput() {
        return process.getInputStream();
    }

    OutputStream mcpOutput() {
        return process.getOutputStream();
    }

    @Override public void close() {
        try {
            process.getOutputStream().close();
            if (!process.waitFor(Duration.ofSeconds(8).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("failed to close arena MCP stdin", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while stopping arena MCP", failure);
        }
        assertEquals(0, process.exitValue(), "arena MCP process exit code");
    }
}
