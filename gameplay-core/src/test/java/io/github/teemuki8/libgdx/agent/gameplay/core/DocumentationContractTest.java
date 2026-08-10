package io.github.teemuki8.libgdx.agent.gameplay.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class DocumentationContractTest {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)");
    private static final List<String> PUBLISHED = List.of(
            "gameplay-core", "gameplay-libgdx", "gameplay-runtime", "gameplay-box2d");

    @Test
    void everyPublishedCoordinateAndGuideIsDeclared() throws Exception {
        Path root = repositoryRoot();
        String readme = Files.readString(root.resolve("README.md"));
        for (String module : PUBLISHED) {
            assertTrue(readme.contains("io.github.teemuki8:" + module),
                    () -> "README omits published coordinate " + module);
            assertTrue(Files.readString(root.resolve(module).resolve("build.gradle.kts"))
                    .contains("java-library"), () -> module + " is not a library module");
        }
        assertTrue(readme.contains("gameplay-fixture") && readme.contains("not published"),
                "README must explicitly exclude the fixture from publication");
        assertGuideLinksResolve(root, readme);
        assertNoPlaceholderLanguage(root.resolve("README.md"));
        assertNoPlaceholderLanguage(root.resolve("docs/guides"));
        assertNoPlaceholderLanguage(root.resolve("docs/evidence"));
    }

    @Test
    void releaseWorkflowsCannotRunOnPush() throws Exception {
        Path workflows = repositoryRoot().resolve(".github/workflows");
        assertManualReleaseWorkflow(workflows.resolve("stage-maven-central.yml"), true);
        assertManualReleaseWorkflow(workflows.resolve("manage-maven-central.yml"), false);
    }

    private static void assertGuideLinksResolve(Path root, String readme) {
        Matcher matcher = MARKDOWN_LINK.matcher(readme);
        int guides = 0;
        while (matcher.find()) {
            String target = matcher.group(1);
            if (!target.startsWith("docs/guides/")) {
                continue;
            }
            guides++;
            assertTrue(Files.isRegularFile(root.resolve(target)),
                    () -> "README guide link does not resolve: " + target);
        }
        assertTrue(guides >= 6, "README must link the complete agent-first guide set");
    }

    private static void assertNoPlaceholderLanguage(Path path) throws IOException {
        try (var files = Files.isDirectory(path) ? Files.walk(path) : java.util.stream.Stream.of(path)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".md")
                            || candidate.toString().endsWith(".json"))
                    .toList()) {
                String lower = Files.readString(file).toLowerCase(Locale.ROOT);
                assertFalse(lower.contains("todo"), () -> file + " contains TODO");
                assertFalse(lower.contains("tbd"), () -> file + " contains TBD");
                assertFalse(lower.contains("pending running"),
                        () -> file + " contains pending placeholder language");
            }
        }
    }

    private static void assertManualReleaseWorkflow(Path path, boolean releaseTrigger)
            throws IOException {
        String workflow = Files.readString(path);
        assertFalse(workflow.matches("(?s).*\\bon:\\s*push:.*"),
                () -> path + " must not run on push");
        assertTrue(workflow.contains("workflow_dispatch:"),
                () -> path + " must support explicit manual dispatch");
        if (releaseTrigger) {
            assertTrue(workflow.contains("release:") && workflow.contains("published"),
                    () -> path + " must stage only a published release or manual dispatch");
        } else {
            assertFalse(workflow.contains("release:"),
                    () -> path + " must remain manual-only");
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
