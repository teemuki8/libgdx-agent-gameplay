import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.JvmLibrary
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.language.base.artifact.SourcesArtifact

plugins {
    base
}

val publishedModules = listOf(
    "gameplay-core",
    "gameplay-libgdx",
    "gameplay-runtime",
    "gameplay-box2d",
)
val releaseVersion = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT")
val junitJupiter = libs.junit.jupiter
val junitPlatformLauncher = libs.junit.platform.launcher

allprojects {
    group = "io.github.teemuki8"
    version = releaseVersion.get()
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")

    dependencyLocking {
        lockAllConfigurations()
    }

    dependencies {
        add("testImplementation", junitJupiter)
        add("testRuntimeOnly", junitPlatformLauncher)
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<CheckstyleExtension> {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        maxWarnings = 0
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    tasks.withType<Javadoc>().configureEach {
        isFailOnError = true
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xmaxwarns", "1000")
            addBooleanOption("Xdoclint:all,-missing", true)
            addBooleanOption("Werror", true)
        }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

val ideaToolingVerification = configurations.create("ideaToolingVerification") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    ideaToolingVerification("gradle:gradle:${gradle.gradleVersion}:src@zip")
    ideaToolingVerification(
        "org.jetbrains.kotlin:kotlin-reflect:$embeddedKotlinVersion:sources",
    )
    ideaToolingVerification(
        "org.jetbrains.kotlin:kotlin-stdlib:$embeddedKotlinVersion:sources",
    )
}

tasks.register("resolveVerificationArtifacts") {
    group = "verification"
    description = "Resolves binaries, sources, and IDEA tooling for dependency verification."

    doLast {
        val componentIds = allprojects
            .flatMap { project -> project.configurations.filter { it.isCanBeResolved } }
            .flatMap { configuration -> configuration.incoming.resolutionResult.allComponents }
            .mapNotNull { result -> result.id as? ModuleComponentIdentifier }
            .distinct()
        check(componentIds.size <= 4_096) {
            "verification artifact graph exceeds 4096 components"
        }
        val sources = dependencies.createArtifactResolutionQuery()
            .forComponents(componentIds)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .flatMap { component -> component.getArtifacts(SourcesArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
        check(sources.size <= 4_096) {
            "verification source graph exceeds 4096 artifacts"
        }
        ideaToolingVerification.files.forEach { file -> check(file.isFile) }
        sources.forEach { artifact -> check(artifact.file.isFile) }
        logger.lifecycle(
            "Resolved ${componentIds.size} components and ${sources.size} source artifacts",
        )
    }
}

tasks.register("javadoc") {
    group = "documentation"
    description = "Generates Javadocs for all published gameplay modules."
    dependsOn(publishedModules.map { project(":$it").tasks.named("javadoc") })
}

val verifyStackVersionContract = tasks.register("verifyStackVersionContract") {
    group = "verification"
    description = "Verifies the approved gameplay stack versions and module boundaries."

    val catalog = layout.projectDirectory.file("gradle/libs.versions.toml")
    val wrapper = layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.properties")
    val moduleBuilds = mapOf(
        "gameplay-core" to layout.projectDirectory.file("gameplay-core/build.gradle.kts"),
        "gameplay-libgdx" to layout.projectDirectory.file("gameplay-libgdx/build.gradle.kts"),
        "gameplay-runtime" to layout.projectDirectory.file("gameplay-runtime/build.gradle.kts"),
        "gameplay-box2d" to layout.projectDirectory.file("gameplay-box2d/build.gradle.kts"),
        "gameplay-fixture" to layout.projectDirectory.file("gameplay-fixture/build.gradle.kts"),
    )
    inputs.files(listOf(catalog, wrapper) + moduleBuilds.values)

    doLast {
        val catalogText = catalog.asFile.readText()
        mapOf(
            "gdx" to "1.14.2",
            "jackson" to "2.22.1",
            "agent-runtime" to "2.1.0",
            "harness" to "1.2.1",
            "markup" to "0.5.0",
            "junit" to "6.1.2",
        ).forEach { (name, expected) ->
            check(catalogText.lineSequence().any { it == "$name = \"$expected\"" }) {
                "gradle/libs.versions.toml must declare $name = $expected"
            }
        }
        check(wrapper.asFile.readText().contains("gradle-9.6.1-bin.zip")) {
            "Gradle wrapper must remain at 9.6.1"
        }

        val buildTexts = moduleBuilds.mapValues { (_, file) -> file.asFile.readText() }
        val coreBuild = buildTexts.getValue("gameplay-core")
        check(coreBuild.contains("implementation(libs.jackson.core)")) {
            "gameplay-core must use the bounded Jackson streaming parser"
        }
        check(!coreBuild.contains("libs.gdx")) {
            "gameplay-core must remain GL-free"
        }
        check(!coreBuild.contains("agent.runtime")) {
            "gameplay-core must remain agent-runtime-free"
        }
        mapOf(
            "gameplay-libgdx" to listOf("api(project(\":gameplay-core\"))"),
            "gameplay-runtime" to listOf("api(project(\":gameplay-core\"))"),
            "gameplay-box2d" to listOf("api(project(\":gameplay-core\"))"),
            "gameplay-fixture" to listOf(
                "implementation(project(\":gameplay-core\"))",
                "implementation(project(\":gameplay-libgdx\"))",
                "implementation(project(\":gameplay-runtime\"))",
                "implementation(project(\":gameplay-box2d\"))",
            ),
        ).forEach { (projectName, declarations) ->
            val buildText = buildTexts.getValue(projectName)
            declarations.forEach { declaration ->
                check(buildText.contains(declaration)) {
                    "$projectName must declare $declaration"
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") }, verifyStackVersionContract)
}
