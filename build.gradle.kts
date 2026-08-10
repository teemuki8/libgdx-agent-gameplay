import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.JvmLibrary
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugins.signing.SigningExtension
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.language.base.artifact.SourcesArtifact
import java.util.zip.ZipFile

abstract class VerifyGameplayPublicationArchives : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    @TaskAction
    fun verifyArchives() {
        archives.files.forEach { archiveFile ->
            ZipFile(archiveFile).use { archive ->
                val entries = archive.entries().asSequence().toList()
                listOf("META-INF/LICENSE", "META-INF/NOTICE").forEach { required ->
                    check(archive.getEntry(required) != null) {
                        "${archiveFile.name} does not contain $required"
                    }
                }
                check(entries.map { it.time }.distinct().size <= 1) {
                    "${archiveFile.name} has non-reproducible entry timestamps"
                }
                check(entries.none {
                    it.name.contains("/fixture/") || it.name.startsWith("ui/")
                        || it.name.startsWith("art/") || it.name.startsWith("gameplay/")
                }) {
                    "${archiveFile.name} leaks fixture code or resources"
                }
                if (!archiveFile.name.contains("-sources")
                    && !archiveFile.name.contains("-javadoc")
                ) {
                    check(entries.any { it.name.endsWith(".class") }) {
                        "${archiveFile.name} contains no library classes"
                    }
                }
            }
        }
    }
}

abstract class VerifyGameplayPublishedPoms : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val poms: ConfigurableFileCollection

    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val expectedVersion: Property<String>

    @get:Input
    abstract val projectUrl: Property<String>

    @TaskAction
    fun verifyPoms() {
        modules.get().forEach { module ->
            val marker = "/$module/build/publications/"
            val pomFile = poms.files.single { file ->
                file.invariantSeparatorsPath.contains(marker)
            }
            val pom = pomFile.readText()
            listOf(
                "<groupId>io.github.teemuki8</groupId>",
                "<artifactId>$module</artifactId>",
                "<version>${expectedVersion.get()}</version>",
                "<url>${projectUrl.get()}</url>",
                "<name>Teemu Jääskeläinen</name>",
                "<email>teemuki8@users.noreply.github.com</email>",
                "<name>The Apache License, Version 2.0</name>",
            ).forEach { required ->
                check(pom.contains(required)) { "$module POM omits $required" }
            }
            check(!pom.contains("unspecified") && !pom.contains("LATEST")
                && !Regex("<version>[^<]*\\+[^<]*</version>").containsMatchIn(pom)
            ) {
                "$module POM contains a project-only or dynamic dependency version"
            }
        }
    }
}

plugins {
    base
}

dependencyLocking {
    lockAllConfigurations()
}

val publishedModules = listOf(
    "gameplay-core",
    "gameplay-libgdx",
    "gameplay-runtime",
    "gameplay-box2d",
)
val releaseVersion = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT")
val repositoryUrl = "https://github.com/teemuki8/libgdx-agent-gameplay"
val mavenCentralStagingUrl =
    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
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

    tasks.withType<Jar>().configureEach {
        manifest.attributes["Implementation-Version"] = project.version
    }

    if (name in publishedModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        tasks.withType<Jar>().configureEach {
            from(rootProject.file("LICENSE")) { into("META-INF") }
            from(rootProject.file("NOTICE")) { into("META-INF") }
        }

        extensions.configure<PublishingExtension> {
            publications.create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name
                pom {
                    name.set("libGDX Agent Gameplay ${project.name}")
                    description.set(
                        "Deterministic bounded gameplay construction for agent-driven libGDX games",
                    )
                    url.set(repositoryUrl)
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("teemuki8")
                            name.set("Teemu Jääskeläinen")
                            email.set("teemuki8@users.noreply.github.com")
                            url.set("https://github.com/teemuki8")
                        }
                    }
                    scm {
                        connection.set("scm:git:$repositoryUrl.git")
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/teemuki8/libgdx-agent-gameplay.git",
                        )
                        url.set(repositoryUrl)
                    }
                }
            }
            repositories {
                maven {
                    name = "mavenCentral"
                    url = uri(mavenCentralStagingUrl)
                    credentials {
                        username = providers.environmentVariable(
                            "MAVEN_CENTRAL_USERNAME",
                        ).orNull
                        password = providers.environmentVariable(
                            "MAVEN_CENTRAL_PASSWORD",
                        ).orNull
                    }
                }
                providers.gradleProperty("qualificationRepository").orNull?.let { location ->
                    maven {
                        name = "qualification"
                        url = uri(location)
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            val key = providers.environmentVariable("MAVEN_SIGNING_KEY")
            val password = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")
            if (key.isPresent && password.isPresent) {
                useInMemoryPgpKeys(key.get(), password.get())
                sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
            }
        }
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

val japicmpClasspath = configurations.create("japicmpClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.STANDARD_JVM),
        )
    }
}
dependencies {
    japicmpClasspath(libs.japicmp)
}
val apiBaselineVersion = providers.gradleProperty("apiBaselineVersion")
val apiCompatibilityTasks = publishedModules.map { module ->
    val suffix = module.split('-').joinToString("") { part ->
        part.replaceFirstChar(Char::uppercaseChar)
    }
    val baseline = configurations.create("${module}ApiBaseline") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    if (apiBaselineVersion.isPresent) {
        dependencies.add(
            baseline.name,
            "io.github.teemuki8:$module:${apiBaselineVersion.get()}@jar",
        )
    }
    if (apiBaselineVersion.isPresent) {
        tasks.register<JavaExec>("apiCompatibility$suffix") {
            group = "verification"
            description = "Checks $module against the configured released API baseline."
            dependsOn(project(":$module").tasks.named("jar"))
            classpath = japicmpClasspath
            mainClass.set("japicmp.JApiCmp")
            notCompatibleWithConfigurationCache(
                "japicmp arguments resolve the optional released baseline",
            )
            doFirst {
                val current = project(":$module").tasks.named<Jar>("jar")
                    .get().archiveFile.get().asFile
                args(
                    "--old", baseline.singleFile.absolutePath,
                    "--new", current.absolutePath,
                    "--only-modified",
                    "--error-on-binary-incompatibility",
                    "--error-on-source-incompatibility",
                    "--ignore-missing-classes",
                )
            }
        }
    } else {
        tasks.register("apiCompatibility$suffix") {
            group = "verification"
            description = "Reports the intentional initial-release API baseline skip for $module."
            doLast {
                logger.lifecycle("Skipping $name: no released API baseline exists yet")
            }
        }
    }
}

tasks.register("apiCompatibility") {
    group = "verification"
    description = "Runs API compatibility or explicitly skips until a baseline is configured."
    dependsOn(apiCompatibilityTasks)
}

val verifyPublicationArchives = tasks.register<VerifyGameplayPublicationArchives>(
    "verifyPublicationArchives",
) {
    group = "verification"
    description = "Verifies reproducible licensed archives and fixture isolation."
    val archives = publishedModules.flatMap { module ->
        listOf("jar", "sourcesJar", "javadocJar").map { task ->
            project(":$module").tasks.named<Jar>(task)
        }
    }
    dependsOn(archives)
    this.archives.from(archives.map { provider -> provider.flatMap { it.archiveFile } })
}

val verifyPublishedPoms = tasks.register<VerifyGameplayPublishedPoms>("verifyPublishedPoms") {
    group = "verification"
    description = "Verifies exact coordinates and required Maven Central POM metadata."
    val pomTasks = publishedModules.map { module ->
        project(":$module").tasks.named("generatePomFileForMavenJavaPublication")
    }
    val pomFiles = publishedModules.map { module ->
        rootProject.layout.projectDirectory.file(
            "$module/build/publications/mavenJava/pom-default.xml",
        ).asFile
    }
    dependsOn(pomTasks)
    poms.from(pomFiles)
    modules.set(publishedModules)
    expectedVersion.set(project.version.toString())
    projectUrl.set(repositoryUrl)
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
            "japicmp" to "0.23.1",
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
    dependsOn(
        subprojects.map { it.tasks.named("check") },
        verifyStackVersionContract,
        verifyPublicationArchives,
        verifyPublishedPoms,
    )
}
