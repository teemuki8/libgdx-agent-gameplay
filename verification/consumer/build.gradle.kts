plugins {
    application
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

application {
    mainClass.set("example.ConsumerSmoke")
}

dependencies {
    implementation("io.github.teemuki8:gameplay-core:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-libgdx:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-runtime:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-box2d:1.0.0-SNAPSHOT")
    implementation("io.github.teemuki8:gameplay-bullet:1.0.0-SNAPSHOT")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.14.2:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-bullet-platform:1.14.2:natives-desktop")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
