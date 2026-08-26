package io.github.teemuki8.libgdx.agent.gameplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BuildContractTest {
    @Test
    void pinsApprovedStackAndKeepsCoreGlFree() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        String versions = Files.readString(root.resolve("gradle/libs.versions.toml"));
        String core = Files.readString(root.resolve("gameplay-core/build.gradle.kts"));
        assertTrue(versions.contains("gdx = \"1.14.2\""));
        assertTrue(versions.contains("agent-runtime = \"2.2.0\""));
        assertTrue(versions.contains("harness = \"1.2.1\""));
        assertTrue(versions.contains("markup = \"0.5.0\""));
        assertTrue(versions.contains("jackson = \"2.22.1\""));
        assertTrue(core.contains("implementation(libs.jackson.core)"));
        assertFalse(core.contains("libs.gdx"));
        assertFalse(core.contains("agent.runtime"));
        String wrapper = Files.readString(
                root.resolve("gradle/wrapper/gradle-wrapper.properties"));
        String distribution = wrapper.lines()
                .filter(line -> line.startsWith("distributionUrl="))
                .findFirst()
                .orElseThrow();
        assertEquals("9.7.0", distribution.replaceAll(
                ".*gradle-([0-9.]+)-bin.*", "$1"));
    }
}
